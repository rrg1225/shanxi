package com.aidecomposer.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aidecomposer.rag.dto.DocumentProcessResponse;
import com.aidecomposer.rag.dto.RagDocumentView;
import com.aidecomposer.rag.dto.RagSearchRequest;
import com.aidecomposer.rag.dto.RagSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RagOrchestrationServiceImpl implements RagOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(RagOrchestrationServiceImpl.class);

    private final AiGatewayRagClient aiGatewayRagClient;
    private final RagDocumentService ragDocumentService;
    private final RagDocumentChunkService ragDocumentChunkService;

    public RagOrchestrationServiceImpl(AiGatewayRagClient aiGatewayRagClient,
                                       RagDocumentService ragDocumentService,
                                       RagDocumentChunkService ragDocumentChunkService) {
        this.aiGatewayRagClient = aiGatewayRagClient;
        this.ragDocumentService = ragDocumentService;
        this.ragDocumentChunkService = ragDocumentChunkService;
    }

    @Override
    public DocumentProcessResponse uploadAndProcess(MultipartFile file,
                                                    Long tenantId,
                                                    String visibility,
                                                    Long ownerUserId,
                                                    Long teacherUserId,
                                                    Integer chunkSize,
                                                    Integer chunkOverlap,
                                                    RagUploadProgressListener progressListener) {
        RagUploadProgressListener L = progressListener == null ? RagUploadProgressListener.NOOP : progressListener;
        L.onProgress(5, "accepted");
        L.onProgress(12, "gateway");
        DocumentProcessResponse response = aiGatewayRagClient.processDocument(
                file, tenantId, visibility, ownerUserId, teacherUserId, chunkSize, chunkOverlap
        );
        if (response == null) {
            throw new RuntimeException("ai-gateway returns empty document response");
        }
        L.onProgress(68, "gateway_done");

        LocalDateTime now = LocalDateTime.now();
        RagDocument doc = new RagDocument();
        doc.setId(response.getDocumentId());
        doc.setTenantId(tenantId);
        doc.setOwnerUserId(ownerUserId);
        doc.setTeacherUserId(teacherUserId);
        doc.setKbScope("PUBLIC".equalsIgnoreCase(visibility) ? "PUBLIC" : "PRIVATE");
        doc.setIsPublic("PUBLIC".equalsIgnoreCase(visibility));
        doc.setCategory("general");
        doc.setTitle(file.getOriginalFilename());
        doc.setDocType(detectDocType(file.getOriginalFilename()));
        doc.setChunkStrategy("RecursiveCharacterTextSplitter");
        doc.setChunkCount(response.getChunkCount());
        doc.setMilvusCollection("career_knowledge_base");
        doc.setStatus("INDEXED");
        doc.setGmtCreated(now);
        doc.setGmtModified(now);
        L.onProgress(72, "persist");
        // 容错：即使 MySQL 不可达，也不要阻断前端可视化/交互（先返回 ai-gateway 的结果）
        try {
            ragDocumentService.saveOrUpdate(doc);

            if (response.getChunks() != null && !response.getChunks().isEmpty()) {
                List<RagDocumentChunk> chunks = response.getChunks().stream().map(item -> {
                    RagDocumentChunk chunk = new RagDocumentChunk();
                    chunk.setId(item.getChunkId());
                    chunk.setDocumentId(response.getDocumentId());
                    chunk.setTenantId(tenantId);
                    chunk.setOwnerUserId(ownerUserId);
                    chunk.setChunkIndex(item.getChunkIndex());
                    chunk.setContent(item.getTextPreview());
                    chunk.setMilvusEntityId(item.getChunkId() == null ? null : String.valueOf(item.getChunkId()));
                    chunk.setGmtCreated(now);
                    chunk.setGmtModified(now);
                    return chunk;
                }).collect(Collectors.toList());
                ragDocumentChunkService.saveBatch(chunks);
            }
        } catch (Exception e) {
            log.warn("MySQL write failed, continue returning gateway result. tenantId={}, err={}", tenantId, e.toString());
        }

        L.onProgress(100, "done");
        return response;
    }

    @Override
    public List<RagDocumentView> listDocuments(Long tenantId, Long ownerUserId, String kbScope, String scope) {
        LambdaQueryWrapper<RagDocument> query = Wrappers.lambdaQuery();

        if (tenantId != null) {
            query.eq(RagDocument::getTenantId, tenantId);
        }

        String scopeNorm = scope == null ? "" : scope.trim().toLowerCase();
        if (!scopeNorm.isEmpty()) {
            switch (scopeNorm) {
                case "public" -> query.eq(RagDocument::getIsPublic, true);
                case "private" -> {
                    if (ownerUserId == null) {
                        throw new IllegalArgumentException("ownerUserId is required when scope=private");
                    }
                    query.eq(RagDocument::getOwnerUserId, ownerUserId)
                            .eq(RagDocument::getIsPublic, false);
                }
                case "all" -> {
                    if (ownerUserId == null) {
                        throw new IllegalArgumentException("ownerUserId is required when scope=all");
                    }
                    query.and(w -> w.eq(RagDocument::getOwnerUserId, ownerUserId)
                            .or()
                            .eq(RagDocument::getIsPublic, true));
                }
                default -> throw new IllegalArgumentException("scope must be one of: all, public, private");
            }
        } else {
            if (ownerUserId != null) {
                query.eq(RagDocument::getOwnerUserId, ownerUserId);
            }
            if (kbScope != null && !kbScope.isBlank()) {
                query.eq(RagDocument::getKbScope, kbScope);
            }
        }

        query.orderByDesc(RagDocument::getGmtCreated);
        try {
            return ragDocumentService.list(query).stream().map(this::toView).collect(Collectors.toList());
        } catch (Exception e) {
            // MySQL 未启动或连接失败时避免 500：与 upload 侧「先网关后落库」策略一致，前端可降级为空列表
            log.warn("listDocuments failed (often DB unreachable). tenantId={}, scope={}, err={}",
                    tenantId, scope, e.toString());
            return Collections.emptyList();
        }
    }

    @Override
    public RagSearchResponse ragSearch(RagSearchRequest request) {
        List<Long> requestedDocumentIds = request.getDocumentIds();
        if (requestedDocumentIds != null && !requestedDocumentIds.isEmpty()) {
            // 限定为“公共知识库 + 指定 document_id 范围”
            Set<Long> allowedDocIds = new HashSet<>(ragDocumentService.list(
                    new LambdaQueryWrapper<RagDocument>()
                            .eq(RagDocument::getTenantId, request.getTenantId())
                            .eq(RagDocument::getIsPublic, true)
                            .in(RagDocument::getId, requestedDocumentIds)
            ).stream().map(RagDocument::getId).toList());

            if (allowedDocIds.isEmpty()) {
                RagSearchResponse empty = new RagSearchResponse();
                empty.setQuery(request.getQuery());
                empty.setTopK(request.getTopK());
                return empty;
            }

            request.setVisibilityMode("PUBLIC");
            request.setDocumentIds(allowedDocIds.stream().toList());
        }

        RagSearchResponse response = aiGatewayRagClient.ragSearch(request);
        if (response == null) {
            return new RagSearchResponse();
        }

        // 兜底过滤：即使网关未按 documentIds 严格过滤，这里再次限定结果范围
        if (requestedDocumentIds != null && !requestedDocumentIds.isEmpty() && response.getResults() != null) {
            Set<Long> allowed = new HashSet<>(request.getDocumentIds());
            response.setResults(response.getResults().stream()
                    .filter(hit -> hit.getDocumentId() != null && allowed.contains(hit.getDocumentId()))
                    .collect(Collectors.toList()));
        }
        return response;
    }

    private RagDocumentView toView(RagDocument doc) {
        RagDocumentView view = new RagDocumentView();
        view.setId(doc.getId());
        view.setTenantId(doc.getTenantId());
        view.setOwnerUserId(doc.getOwnerUserId());
        view.setTeacherUserId(doc.getTeacherUserId());
        view.setKbScope(doc.getKbScope());
        view.setIsPublic(doc.getIsPublic());
        view.setCategory(doc.getCategory());
        view.setTitle(doc.getTitle());
        view.setDocType(doc.getDocType());
        view.setChunkCount(doc.getChunkCount());
        view.setStatus(doc.getStatus());
        view.setMilvusCollection(doc.getMilvusCollection());
        view.setGmtCreated(doc.getGmtCreated());
        view.setGmtModified(doc.getGmtModified());
        return view;
    }

    private String detectDocType(String filename) {
        if (filename == null) {
            return "TEXT";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".md") || lower.endsWith(".txt")) {
            return "TEXT";
        }
        return "TEXT";
    }
}

