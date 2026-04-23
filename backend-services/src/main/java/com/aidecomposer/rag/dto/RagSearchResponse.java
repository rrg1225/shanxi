package com.aidecomposer.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class RagSearchResponse {
    private String query;
    private Integer topK;
    private List<RagSearchHit> results = new ArrayList<>();

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public List<RagSearchHit> getResults() {
        return results;
    }

    public void setResults(List<RagSearchHit> results) {
        this.results = results;
    }
}

