package com.aidecomposer.tutor;

import com.aidecomposer.tutor.dto.TutorHintRequest;
import com.aidecomposer.tutor.dto.TutorHintResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/tutor")
public class TutorController {

    private final TutorService tutorService;

    public TutorController(TutorService tutorService) {
        this.tutorService = tutorService;
    }

    @PostMapping("/hint")
    public TutorHintResponse hint(@RequestBody(required = false) TutorHintRequest request) {
        if (request == null) {
            request = new TutorHintRequest();
        }
        return tutorService.generateHints(request);
    }
}

