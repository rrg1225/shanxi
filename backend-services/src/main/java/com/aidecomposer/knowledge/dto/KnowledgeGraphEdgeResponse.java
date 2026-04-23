package com.aidecomposer.knowledge.dto;

public class KnowledgeGraphEdgeResponse {
    private Long sourceChunkId;
    private Long targetChunkId;
    private Double similarityScore;

    public Long getSourceChunkId() {
        return sourceChunkId;
    }

    public void setSourceChunkId(Long sourceChunkId) {
        this.sourceChunkId = sourceChunkId;
    }

    public Long getTargetChunkId() {
        return targetChunkId;
    }

    public void setTargetChunkId(Long targetChunkId) {
        this.targetChunkId = targetChunkId;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }
}
