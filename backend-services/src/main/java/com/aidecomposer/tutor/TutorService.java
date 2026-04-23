package com.aidecomposer.tutor;

import com.aidecomposer.tutor.dto.TutorHintRequest;
import com.aidecomposer.tutor.dto.TutorHintResponse;

public interface TutorService {
    TutorHintResponse generateHints(TutorHintRequest request);
}

