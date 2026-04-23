package com.aidecomposer.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class RagSearchRequest {

    @NotBlank
    private String query;

    @NotNull
    private Long tenantId;

    private String visibilityMode = "BOTH";

    private Long ownerUserId;

    private Integer topK = 6;

    /**
     * 可选：限定检索的 document_id 范围。
     * 当指定后，后端将只允许在该范围对应的公共文档中检索。
     */
    private List<Long> documentIds;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getVisibilityMode() {
        return visibilityMode;
    }

    public void setVisibilityMode(String visibilityMode) {
        this.visibilityMode = visibilityMode;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public List<Long> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<Long> documentIds) {
        this.documentIds = documentIds;
    }
}

