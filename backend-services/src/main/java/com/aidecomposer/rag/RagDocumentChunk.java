package com.aidecomposer.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("rag_document_chunk")
public class RagDocumentChunk {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("document_id")
    private Long documentId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("owner_user_id")
    private Long ownerUserId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    @TableField("content")
    private String content;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("milvus_entity_id")
    private String milvusEntityId;

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

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
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

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public String getMilvusEntityId() {
        return milvusEntityId;
    }

    public void setMilvusEntityId(String milvusEntityId) {
        this.milvusEntityId = milvusEntityId;
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

