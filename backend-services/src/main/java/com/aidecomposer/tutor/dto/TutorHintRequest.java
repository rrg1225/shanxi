package com.aidecomposer.tutor.dto;

import java.util.Map;

public class TutorHintRequest {
    private String module;
    private String lastError;
    private String recentAction;
    private String userLevel;
    private Map<String, Object> extraContext;

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getRecentAction() {
        return recentAction;
    }

    public void setRecentAction(String recentAction) {
        this.recentAction = recentAction;
    }

    public String getUserLevel() {
        return userLevel;
    }

    public void setUserLevel(String userLevel) {
        this.userLevel = userLevel;
    }

    public Map<String, Object> getExtraContext() {
        return extraContext;
    }

    public void setExtraContext(Map<String, Object> extraContext) {
        this.extraContext = extraContext;
    }
}

