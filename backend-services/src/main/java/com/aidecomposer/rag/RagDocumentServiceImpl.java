package com.aidecomposer.rag;

import com.aidecomposer.knowledge.KnowledgeNodeMaterialCatalog;
import com.aidecomposer.rag.dto.PublicKnowledgeGraphLink;
import com.aidecomposer.rag.dto.PublicKnowledgeGraphNode;
import com.aidecomposer.rag.dto.PublicKnowledgeGraphResponse;
import com.aidecomposer.rag.dto.RagDocumentView;
import com.aidecomposer.rag.dto.RagMarketplaceItemResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RagDocumentServiceImpl extends ServiceImpl<RagDocumentMapper, RagDocument> implements RagDocumentService {

    private static final Logger log = LoggerFactory.getLogger(RagDocumentServiceImpl.class);

    private final ObjectMapper objectMapper;

    public RagDocumentServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public PublicKnowledgeGraphResponse getPublicKnowledgeGraph(Long tenantId, String category) {
        LambdaQueryWrapper<RagDocument> query = new LambdaQueryWrapper<RagDocument>()
                .eq(tenantId != null, RagDocument::getTenantId, tenantId)
                .eq(RagDocument::getIsPublic, true)
                .eq(category != null && !category.isBlank(), RagDocument::getCategory, category)
                .orderByDesc(RagDocument::getChunkCount)
                .orderByDesc(RagDocument::getGmtCreated);

        List<RagDocument> docs;
        try {
            docs = this.list(query);
        } catch (Exception e) {
            log.warn("getPublicKnowledgeGraph list failed (often DB unreachable). tenantId={}, err={}",
                    tenantId, e.toString());
            PublicKnowledgeGraphResponse empty = new PublicKnowledgeGraphResponse();
            empty.setNodes(List.of());
            empty.setLinks(List.of());
            return empty;
        }
        PublicKnowledgeGraphResponse response = new PublicKnowledgeGraphResponse();

        List<PublicKnowledgeGraphNode> nodes = docs.stream().map(doc -> {
            PublicKnowledgeGraphNode node = new PublicKnowledgeGraphNode();
            node.setId(doc.getId());
            node.setTitle(doc.getTitle());
            node.setCategory(doc.getCategory());
            node.setChunkCount(doc.getChunkCount());
            node.setDocType(doc.getDocType());
            return node;
        }).toList();

        // 关系构造策略（可后续升级）：相同 category 的公共文档两两连边
        List<PublicKnowledgeGraphLink> links = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            for (int j = i + 1; j < docs.size(); j++) {
                RagDocument left = docs.get(i);
                RagDocument right = docs.get(j);
                if (left.getCategory() == null || !left.getCategory().equals(right.getCategory())) {
                    continue;
                }
                PublicKnowledgeGraphLink link = new PublicKnowledgeGraphLink();
                link.setSource(left.getId());
                link.setTarget(right.getId());
                link.setRelation("SAME_CATEGORY");
                links.add(link);
            }
        }

        response.setNodes(nodes);
        response.setLinks(links);
        return response;
    }

    @Override
    public List<RagDocumentView> listPublicBooksByKnowledgeNode(String nodeId, Long tenantId) {
        Optional<KnowledgeNodeMaterialCatalog.MaterialMatchRule> ruleOpt =
                KnowledgeNodeMaterialCatalog.ruleFor(nodeId);
        if (ruleOpt.isEmpty()) {
            return List.of();
        }
        List<String> categories = ruleOpt.get().categories().stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        if (categories.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<RagDocument> q = new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getTenantId, tenantId)
                .eq(RagDocument::getIsPublic, true)
                .in(RagDocument::getCategory, categories)
                .orderByDesc(RagDocument::getChunkCount)
                .orderByDesc(RagDocument::getGmtCreated);

        List<RagDocument> docs;
        try {
            docs = this.list(q);
        } catch (Exception e) {
            log.warn("listPublicBooksByKnowledgeNode failed tenantId={}, nodeId={}, err={}",
                    tenantId, nodeId, e.toString());
            return List.of();
        }
        return docs.stream().map(this::toRagDocumentView).toList();
    }

    @Override
    public List<RagMarketplaceItemResponse> listMarketplacePublic(Long tenantId, String category) {
        boolean filterCategory = category != null
                && !category.isBlank()
                && !"全部".equals(category.trim());
        String catTrimmed = filterCategory ? category.trim() : null;

        LambdaQueryWrapper<RagDocument> q = new LambdaQueryWrapper<RagDocument>()
                .eq(tenantId != null, RagDocument::getTenantId, tenantId)
                .eq(RagDocument::getIsPublic, true)
                .eq(filterCategory, RagDocument::getCategory, catTrimmed)
                .orderByDesc(RagDocument::getChunkCount)
                .orderByDesc(RagDocument::getGmtCreated);

        List<RagDocument> docs;
        try {
            docs = this.list(q);
        } catch (Exception e) {
            log.warn("listMarketplacePublic failed tenantId={}, category={}, err={}", tenantId, category, e.toString());
            return List.of();
        }
        return docs.stream().map(this::toMarketplaceItem).toList();
    }

    private RagMarketplaceItemResponse toMarketplaceItem(RagDocument doc) {
        RagMarketplaceItemResponse r = new RagMarketplaceItemResponse();
        r.setDocumentId(doc.getId());
        r.setTitle(doc.getTitle());
        r.setCategory(doc.getCategory());

        String metaStr = doc.getMeta();
        if (metaStr != null && !metaStr.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(metaStr);
                String summary = textNode(root, "summary");
                if (summary != null && !summary.isBlank()) {
                    r.setSummary(summary.trim());
                }
                String preview = firstNonBlank(textNode(root, "previewImageUrl"), textNode(root, "coverUrl"));
                if (preview != null && !preview.isBlank()) {
                    r.setPreviewImageUrl(preview.trim());
                }
                String nodeId = firstNonBlank(textNode(root, "knowledgeNodeId"), firstArrayText(root, "knowledgeNodeIds"));
                if (nodeId != null && !nodeId.isBlank()) {
                    r.setKnowledgeNodeId(nodeId.trim());
                }
            } catch (Exception e) {
                log.debug("marketplace meta parse skip id={} err={}", doc.getId(), e.toString());
            }
        }
        if (r.getSummary() == null || r.getSummary().isBlank()) {
            r.setSummary(marketplaceSummaryFallback(doc));
        }
        if (r.getPreviewImageUrl() == null) {
            r.setPreviewImageUrl("");
        }
        return r;
    }

    private static String textNode(JsonNode root, String field) {
        if (root == null || field == null) {
            return null;
        }
        JsonNode n = root.get(field);
        return n == null || n.isNull() || !n.isTextual() ? null : n.asText();
    }

    private static String firstArrayText(JsonNode root, String arrayField) {
        if (root == null || arrayField == null) {
            return null;
        }
        JsonNode arr = root.get(arrayField);
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return null;
        }
        JsonNode first = arr.get(0);
        return first != null && first.isTextual() ? first.asText() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String marketplaceSummaryFallback(RagDocument doc) {
        String t = doc.getTitle() == null ? "" : doc.getTitle();
        String c = doc.getCategory() == null ? "" : doc.getCategory();
        String dt = doc.getDocType() == null ? "" : doc.getDocType();
        return ("【" + c + " · " + dt + "】" + t).trim();
    }

    private RagDocumentView toRagDocumentView(RagDocument doc) {
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
}

