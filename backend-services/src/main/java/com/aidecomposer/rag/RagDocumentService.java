package com.aidecomposer.rag;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aidecomposer.rag.dto.PublicKnowledgeGraphResponse;
import com.aidecomposer.rag.dto.RagDocumentView;
import com.aidecomposer.rag.dto.RagMarketplaceItemResponse;

import java.util.List;

public interface RagDocumentService extends IService<RagDocument> {
    PublicKnowledgeGraphResponse getPublicKnowledgeGraph(Long tenantId, String category);

    /**
     * 学习资源：按知识图谱节点 id 解析允许的 {@code rag_document.category}，返回 {@code is_public = 1} 的文档列表。
     */
    List<RagDocumentView> listPublicBooksByKnowledgeNode(String nodeId, Long tenantId);

    /**
     * 资源广场：返回 {@code is_public = 1} 的公开资源，可按 {@code category}（如：学习/编程/求职）筛选。
     */
    List<RagMarketplaceItemResponse> listMarketplacePublic(Long tenantId, String category);
}

