package com.aidecomposer.rag.dto;

/**
 * 资源广场公开资源条目：供 {@code GET /api/v1/rag/marketplace} 返回。
 * <p>摘要与预览图优先来自 {@code rag_document.meta} JSON；
 * 兼容字段名 {@code coverUrl}、{@code knowledgeNodeIds[]}。</p>
 */
public class RagMarketplaceItemResponse {

    private Long documentId;
    private String title;
    private String summary;
    private String previewImageUrl;
    /** 知识图谱节点 id（与前端 KnowledgeUniverse / 学习路径一致） */
    private String knowledgeNodeId;
    /** 资源分类（与表 {@code rag_document.category} 一致，如：学习/编程/求职） */
    private String category;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPreviewImageUrl() {
        return previewImageUrl;
    }

    public void setPreviewImageUrl(String previewImageUrl) {
        this.previewImageUrl = previewImageUrl;
    }

    public String getKnowledgeNodeId() {
        return knowledgeNodeId;
    }

    public void setKnowledgeNodeId(String knowledgeNodeId) {
        this.knowledgeNodeId = knowledgeNodeId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
