package com.aidecomposer.rag;

import com.aidecomposer.rag.dto.DocumentProcessResponse;
import com.aidecomposer.rag.dto.PublicKnowledgeGraphResponse;
import com.aidecomposer.rag.dto.RagDocumentView;
import com.aidecomposer.rag.dto.RagMarketplaceItemResponse;
import com.aidecomposer.rag.dto.RagSearchRequest;
import com.aidecomposer.rag.dto.RagSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * RAG 文档与检索 HTTP 入口。
 * <p>知识图谱边（向量自动生成）的查询接口在 {@link com.aidecomposer.knowledge.KnowledgeGraphV1Controller}：
 * {@code GET /api/v1/knowledge-graph/edges}（与 {@code /api/rag} 前缀分离，便于 API 版本化）。</p>
 */
@RestController
public class RagController {

    private static final long UPLOAD_SSE_TIMEOUT_MS = 30L * 60 * 1000;

    private final RagOrchestrationService ragOrchestrationService;
    private final RagDocumentService ragDocumentService;
    private final ObjectMapper objectMapper;
    private final Executor ragUploadExecutor;

    public RagController(RagOrchestrationService ragOrchestrationService,
                         RagDocumentService ragDocumentService,
                         ObjectMapper objectMapper,
                         @Qualifier("ragUploadExecutor") Executor ragUploadExecutor) {
        this.ragOrchestrationService = ragOrchestrationService;
        this.ragDocumentService = ragDocumentService;
        this.objectMapper = objectMapper;
        this.ragUploadExecutor = ragUploadExecutor;
    }

    /**
     * 异步上传：在后台线程解析/向量化，经 SSE 推送进度（event: progress / complete / error）。
     */
    @PostMapping(value = "/api/rag/documents/upload", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter uploadDocument(@RequestParam("file") MultipartFile file,
                                     @RequestParam("tenantId") Long tenantId,
                                     @RequestParam("ownerUserId") Long ownerUserId,
                                     @RequestParam(value = "teacherUserId", required = false) Long teacherUserId,
                                     @RequestParam(value = "visibility", defaultValue = "PRIVATE") String visibility,
                                     @RequestParam(value = "chunkSize", defaultValue = "800") Integer chunkSize,
                                     @RequestParam(value = "chunkOverlap", defaultValue = "150") Integer chunkOverlap) {
        SseEmitter emitter = new SseEmitter(UPLOAD_SSE_TIMEOUT_MS);
        ragUploadExecutor.execute(() -> {
            try {
                DocumentProcessResponse result = ragOrchestrationService.uploadAndProcess(
                        file,
                        tenantId,
                        visibility,
                        ownerUserId,
                        teacherUserId,
                        chunkSize,
                        chunkOverlap,
                        (percent, phase) -> sendProgress(emitter, percent, phase)
                );
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(objectMapper.writeValueAsString(result), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                try {
                    String errJson = objectMapper.writeValueAsString(Map.of("message", e.getMessage() == null ? "error" : e.getMessage()));
                    emitter.send(SseEmitter.event().name("error").data(errJson, MediaType.APPLICATION_JSON));
                } catch (IOException ignored) {
                    /* ignore */
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendProgress(SseEmitter emitter, int percent, String phase) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("percent", percent, "phase", phase));
            emitter.send(SseEmitter.event().name("progress").data(json, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    @GetMapping("/api/rag/documents")
    public List<RagDocumentView> listDocuments(@RequestParam("tenantId") Long tenantId,
                                               @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
                                               @RequestParam(value = "kbScope", required = false) String kbScope,
                                               @RequestParam(value = "scope", required = false) String scope) {
        try {
            return ragOrchestrationService.listDocuments(tenantId, ownerUserId, kbScope, scope);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/api/rag/search")
    public RagSearchResponse search(@Valid @RequestBody RagSearchRequest request) {
        return ragOrchestrationService.ragSearch(request);
    }

    @GetMapping("/api/rag/public-knowledge-graph")
    public PublicKnowledgeGraphResponse getPublicKnowledgeGraph(@RequestParam(value = "tenantId", required = false) Long tenantId,
                                                                @RequestParam(value = "category", required = false) String category) {
        return ragDocumentService.getPublicKnowledgeGraph(tenantId, category);
    }

    /**
     * 学习资源：按知识图谱节点 id 返回公共书籍（{@code is_public=1}），类目由节点与 {@link com.aidecomposer.knowledge.KnowledgeNodeMaterialCatalog} 映射。
     */
    @GetMapping("/api/v1/rag/books/by-node/{nodeId}")
    public List<RagDocumentView> listBooksByKnowledgeNode(@PathVariable("nodeId") String nodeId,
                                                          @RequestParam(value = "tenantId", defaultValue = "1") Long tenantId) {
        return ragDocumentService.listPublicBooksByKnowledgeNode(nodeId, tenantId);
    }

    /**
     * 资源广场：公开资源列表，支持按 {@code category}（如：学习、编程、求职）筛选；不传或传「全部」则返回全部公开文档。
     */
    @GetMapping("/api/v1/rag/marketplace")
    public List<RagMarketplaceItemResponse> listMarketplace(@RequestParam(value = "tenantId", defaultValue = "1") Long tenantId,
                                                            @RequestParam(value = "category", required = false) String category) {
        return ragDocumentService.listMarketplacePublic(tenantId, category);
    }

    @GetMapping("/api/rag/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "service", "rag");
    }
}

