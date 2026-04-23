package com.aidecomposer.knowledge;

import com.aidecomposer.knowledge.dto.KnowledgeNodeDocumentResponse;
import com.aidecomposer.rag.RagDocument;
import com.aidecomposer.rag.RagDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class KnowledgeNodeDocumentServiceImpl implements KnowledgeNodeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeNodeDocumentServiceImpl.class);

    private final RagDocumentMapper ragDocumentMapper;
    private final ObjectMapper objectMapper;

    public KnowledgeNodeDocumentServiceImpl(RagDocumentMapper ragDocumentMapper, ObjectMapper objectMapper) {
        this.ragDocumentMapper = ragDocumentMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<KnowledgeNodeDocumentResponse> listDocumentsForKnowledgeNode(String nodeId, Long tenantId) {
        Optional<KnowledgeNodeMaterialCatalog.MaterialMatchRule> ruleOpt = KnowledgeNodeMaterialCatalog.ruleFor(nodeId);
        if (ruleOpt.isEmpty()) {
            return List.of();
        }
        KnowledgeNodeMaterialCatalog.MaterialMatchRule rule = ruleOpt.get();

        LambdaQueryWrapper<RagDocument> q = Wrappers.lambdaQuery();
        q.eq(RagDocument::getTenantId, tenantId);
        q.eq(RagDocument::getIsPublic, true);

        q.and(w -> {
            boolean first = true;
            for (String c : rule.categories()) {
                if (c == null || c.isBlank()) {
                    continue;
                }
                if (first) {
                    w.eq(RagDocument::getCategory, c);
                    first = false;
                } else {
                    w.or().eq(RagDocument::getCategory, c);
                }
            }
            for (String kw : rule.titleKeywords()) {
                if (kw == null || kw.isBlank()) {
                    continue;
                }
                if (first) {
                    w.like(RagDocument::getTitle, kw);
                    first = false;
                } else {
                    w.or().like(RagDocument::getTitle, kw);
                }
            }
            if (first) {
                w.apply("meta IS NOT NULL AND JSON_CONTAINS(JSON_EXTRACT(meta, '$.knowledgeNodeIds'), JSON_QUOTE({0}), '$')",
                        nodeId);
            } else {
                w.or().apply("meta IS NOT NULL AND JSON_CONTAINS(JSON_EXTRACT(meta, '$.knowledgeNodeIds'), JSON_QUOTE({0}), '$')",
                        nodeId);
            }
        });

        q.orderByDesc(RagDocument::getChunkCount).orderByDesc(RagDocument::getGmtModified);

        List<RagDocument> rows;
        try {
            rows = ragDocumentMapper.selectList(q);
        } catch (Exception e) {
            log.warn("listDocumentsForKnowledgeNode query failed, tenantId={}, nodeId={}, err={}",
                    tenantId, nodeId, e.toString());
            return List.of();
        }

        Set<Long> seen = new LinkedHashSet<>();
        List<KnowledgeNodeDocumentResponse> out = new ArrayList<>();
        for (RagDocument d : rows) {
            if (d.getId() != null && seen.add(d.getId())) {
                out.add(toResponse(d));
            }
        }
        out.sort((a, b) -> Integer.compare(titleMatchScore(rule, b.getTitle()), titleMatchScore(rule, a.getTitle())));
        return out;
    }

    private static int titleMatchScore(KnowledgeNodeMaterialCatalog.MaterialMatchRule rule, String title) {
        if (title == null) {
            return 0;
        }
        String t = title.toLowerCase(Locale.ROOT);
        int s = 0;
        for (String kw : rule.titleKeywords()) {
            if (kw != null && t.contains(kw.toLowerCase(Locale.ROOT))) {
                s += 2;
            }
        }
        return s;
    }

    private KnowledgeNodeDocumentResponse toResponse(RagDocument doc) {
        KnowledgeNodeDocumentResponse out = new KnowledgeNodeDocumentResponse();
        out.setId(doc.getId());
        out.setTitle(doc.getTitle());
        out.setIsPublic(Boolean.TRUE.equals(doc.getIsPublic()));

        String cover = null;
        String summary = null;
        if (doc.getMeta() != null && !doc.getMeta().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(doc.getMeta());
                if (root.hasNonNull("coverUrl")) {
                    cover = root.get("coverUrl").asText();
                }
                if (root.hasNonNull("summary")) {
                    summary = root.get("summary").asText();
                }
            } catch (Exception e) {
                log.debug("parse rag_document.meta failed id={}", doc.getId());
            }
        }
        if (cover == null || cover.isBlank()) {
            cover = "https://picsum.photos/seed/rag-doc-" + doc.getId() + "/200/280";
        }
        out.setCoverUrl(cover);

        if (summary == null || summary.isBlank()) {
            summary = buildSummaryFallback(doc);
        }
        out.setSummary(summary);
        return out;
    }

    private String buildSummaryFallback(RagDocument doc) {
        String t = doc.getTitle() == null ? "" : doc.getTitle();
        String c = doc.getCategory() == null ? "" : doc.getCategory();
        String dt = doc.getDocType() == null ? "" : doc.getDocType();
        return ("【" + c + " · " + dt + "】" + t).trim();
    }
}
