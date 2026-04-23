package com.aidecomposer.experiment;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 前端保存 Prompt A/B 等实验快照（写入 {@link ExperimentRecord#inputPayload} JSON）。
 */
public class SaveExperimentSnapshotRequest {

    @NotNull
    private Long userId;

    /** 默认 PROMPT_AB */
    private String experimentType = "PROMPT_AB";

    private Map<String, Object> payload;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getExperimentType() {
        return experimentType;
    }

    public void setExperimentType(String experimentType) {
        this.experimentType = experimentType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
