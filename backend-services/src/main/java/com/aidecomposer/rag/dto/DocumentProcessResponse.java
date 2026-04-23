package com.aidecomposer.rag.dto;

import java.util.ArrayList;
import java.util.List;

public class DocumentProcessResponse {
    private Long documentId;
    private Integer chunkCount;
    private Integer embedDim;
    private String embeddingModel;
    private List<DocumentChunkPreview> chunks = new ArrayList<>();

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public Integer getEmbedDim() {
        return embedDim;
    }

    public void setEmbedDim(Integer embedDim) {
        this.embedDim = embedDim;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<DocumentChunkPreview> getChunks() {
        return chunks;
    }

    public void setChunks(List<DocumentChunkPreview> chunks) {
        this.chunks = chunks;
    }

    public static class DocumentChunkPreview {
        private Long chunkId;
        private Integer chunkIndex;
        private String textPreview;
        private List<Double> coords;

        public Long getChunkId() {
            return chunkId;
        }

        public void setChunkId(Long chunkId) {
            this.chunkId = chunkId;
        }

        public Integer getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public String getTextPreview() {
            return textPreview;
        }

        public void setTextPreview(String textPreview) {
            this.textPreview = textPreview;
        }

        public List<Double> getCoords() {
            return coords;
        }

        public void setCoords(List<Double> coords) {
            this.coords = coords;
        }
    }
}

