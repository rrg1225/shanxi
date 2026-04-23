package com.aidecomposer.knowledge.dto;

/**
 * 知识图谱节点关联的公共文档（供前端垂直布局「推荐书籍」等使用）。
 */
public class KnowledgeNodeDocumentResponse {

    private Long id;
    private String title;
    /** 封面；无则前端可用占位图 */
    private String coverUrl;
    /** 简介：来自 meta.summary 或标题摘要 */
    private String summary;
    private Boolean isPublic;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
}
