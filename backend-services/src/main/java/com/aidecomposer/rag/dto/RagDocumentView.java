package com.aidecomposer.rag.dto;

import java.time.LocalDateTime;

public class RagDocumentView {
    private Long id;
    private Long tenantId;
    private Long ownerUserId;
    private Long teacherUserId;
    private String kbScope;
    private Boolean isPublic;
    private String category;
    private String title;
    private String docType;
    private Integer chunkCount;
    private String status;
    private String milvusCollection;
    private LocalDateTime gmtCreated;
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

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMilvusCollection() {
        return milvusCollection;
    }

    public void setMilvusCollection(String milvusCollection) {
        this.milvusCollection = milvusCollection;
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

