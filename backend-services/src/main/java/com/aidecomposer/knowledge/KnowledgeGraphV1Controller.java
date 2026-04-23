package com.aidecomposer.knowledge;

import com.aidecomposer.knowledge.dto.KnowledgeGraphEdgeResponse;
import com.aidecomposer.knowledge.dto.KnowledgeNodeDocumentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识图谱 v1 API。路径与 {@code RagController} 的 /api/rag 分离。
 */
@RestController
public class KnowledgeGraphV1Controller {

    private final KnowledgeGraphQueryService queryService;
    private final KnowledgeNodeDocumentService knowledgeNodeDocumentService;

    public KnowledgeGraphV1Controller(KnowledgeGraphQueryService queryService,
                                      KnowledgeNodeDocumentService knowledgeNodeDocumentService) {
        this.queryService = queryService;
        this.knowledgeNodeDocumentService = knowledgeNodeDocumentService;
    }

    @GetMapping("/api/v1/knowledge-graph/edges")
    public List<KnowledgeGraphEdgeResponse> listEdges(
            @RequestParam(value = "minScore", defaultValue = "0.8") double minScore,
            @RequestParam(value = "maxEdgesPerNode", defaultValue = "5") int maxEdgesPerNode) {
        return queryService.listEdgesFiltered(minScore, maxEdgesPerNode);
    }

    /**
     * 按前端知识星空节点 id（如 cs-foundation、rag-pipeline）查询关联公共书籍/文档。
     */
    @GetMapping("/api/v1/knowledge/node/{nodeId}/documents")
    public List<KnowledgeNodeDocumentResponse> listNodeDocuments(
            @PathVariable("nodeId") String nodeId,
            @RequestParam(value = "tenantId", defaultValue = "1") Long tenantId) {
        return knowledgeNodeDocumentService.listDocumentsForKnowledgeNode(nodeId, tenantId);
    }
}
