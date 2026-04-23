package com.aidecomposer.cache;

import com.aidecomposer.ratelimit.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PromptCacheController {

    private final PromptSmartCacheService cacheService;

    public PromptCacheController(PromptSmartCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public static class PromptGenerateRequest {
        @NotBlank
        public String prompt;

        @Positive
        public double temperature = 0.7;

        @Positive
        public double topP = 0.9;

        @NotNull
        public Long tenantId;

        @NotNull
        public Long userId;
    }

    @PostMapping("/api/ai/prompt-with-cache")
    @RateLimit(
            keyPrefix = "ratelimit:llm",
            keySpel = "#req.tenantId + ':' + #req.userId",
            capacity = 20,
            refillRate = 10,
            permits = 1,
            stateTtlSeconds = 3600
    )
    public Map<String, Object> generate(@Valid @RequestBody PromptGenerateRequest req) {
        PromptSmartCacheService.CachedPromptResponse resp = cacheService.generateWithCache(
                req.tenantId,
                req.userId,
                req.prompt,
                req.temperature,
                req.topP
        );
        return Map.of(
                "code", "OK",
                "cached", resp.isCached(),
                "result", resp.getResult()
        );
    }
}

