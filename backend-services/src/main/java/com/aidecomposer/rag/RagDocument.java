package com.aidecomposer.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("rag_document")
public class RagDocument {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("owner_user_id")
    private Long ownerUserId;

    @TableField("teacher_user_id")
    private Long teacherUserId;

    @TableField("kb_scope")
    private String kbScope;

    /** 对应表列 {@code is_public} TINYINT(1)：1 表示公共知识库文档，参与学习资源等公共检索。 */
    @TableField("is_public")
    private Boolean isPublic;

    /** 知识分类 slug（如 general、RAG、transformer、rag、agent），用于学习资源按节点映射筛选。 */
    @TableField("category")
    private String category;

    @TableField("title")
    private String title;

    @TableField("doc_type")
    private String docType;

    @TableField("source_uri")
    private String sourceUri;

    @TableField("content_hash")
    private String contentHash;

    @TableField("raw_content")
    private String rawContent;

    @TableField("chunk_strategy")
    private String chunkStrategy;

    @TableField("chunk_count")
    private Integer chunkCount;

    @TableField("milvus_collection")
    private String milvusCollection;

    @TableField("status")
    private String status;

    @TableField("meta")
    private String meta;

    @TableField("gmt_created")
    private LocalDateTime gmtCreated;

    @TableField("gmt_modified")
    private LocalDateTime gmtModified;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getTeacherUserId() {
        return teacherUserId;
    }

    public void setTeacherUserId(Long teacherUserId) {
        this.teacherUserId = teacherUserId;
    }

    public String getKbScope() {
        return kbScope;
    }

    public void setKbScope(String kbScope) {
        this.kbScope = kbScope;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public String getChunkStrategy() {
        return chunkStrategy;
    }

    public void setChunkStrategy(String chunkStrategy) {
        this.chunkStrategy = chunkStrategy;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getMilvusCollection() {
        return milvusCollection;
    }

    public void setMilvusCollection(String milvusCollection) {
        this.milvusCollection = milvusCollection;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }

    public LocalDateTime getGmtCreated() {
        return gmtCreated;
    }

    public void setGmtCreated(LocalDateTime gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    public LocalDateTime getGmtModified() {
        return gmtModified;
    }

    public void setGmtModified(LocalDateTime gmtModified) {
        this.gmtModified = gmtModified;
    }
}

