package com.aidecomposer.tutor.dto;

import java.util.ArrayList;
import java.util.List;

public class TutorHintResponse {
    private List<String> tips = new ArrayList<>();

    public List<String> getTips() {
        return tips;
    }

    public void setTips(List<String> tips) {
        this.tips = tips;
    }
}

