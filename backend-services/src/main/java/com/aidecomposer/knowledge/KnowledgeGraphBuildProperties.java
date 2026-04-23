package com.aidecomposer.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.knowledge-graph")
public class KnowledgeGraphBuildProperties {

    private boolean enabled = true;
    private double buildMinScore = 0.5d;
    private int milvusSearchTopK = 48;
    private int chunkIdBatchSize = 80;
    private boolean rebuildTruncateEdges = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getBuildMinScore() {
        return buildMinScore;
    }

    public void setBuildMinScore(double buildMinScore) {
        this.buildMinScore = buildMinScore;
    }

    public int getMilvusSearchTopK() {
        return milvusSearchTopK;
    }

    public void setMilvusSearchTopK(int milvusSearchTopK) {
        this.milvusSearchTopK = milvusSearchTopK;
    }

    public int getChunkIdBatchSize() {
        return chunkIdBatchSize;
    }

    public void setChunkIdBatchSize(int chunkIdBatchSize) {
        this.chunkIdBatchSize = chunkIdBatchSize;
    }

    public boolean isRebuildTruncateEdges() {
        return rebuildTruncateEdges;
    }

    public void setRebuildTruncateEdges(boolean rebuildTruncateEdges) {
        this.rebuildTruncateEdges = rebuildTruncateEdges;
    }
}
