package com.aidecomposer.knowledge;

import com.aidecomposer.knowledge.dto.KnowledgeNodeDocumentResponse;

import java.util.List;

public interface KnowledgeNodeDocumentService {

    /**
     * 按知识节点 id 查询关联公共文档（category / 标题关键字 / meta.knowledgeNodeIds）。
     */
    List<KnowledgeNodeDocumentResponse> listDocumentsForKnowledgeNode(String nodeId, Long tenantId);
}
