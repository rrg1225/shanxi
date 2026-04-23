package com.aidecomposer.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("knowledge_graph_edge")
public class KnowledgeGraphEdge {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("source_chunk_id")
    private Long sourceChunkId;

    @TableField("target_chunk_id")
    private Long targetChunkId;

    @TableField("similarity_score")
    private Double similarityScore;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
