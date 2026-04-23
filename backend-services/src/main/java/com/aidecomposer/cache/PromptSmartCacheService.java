package com.aidecomposer.cache;

import com.aidecomposer.ai.AiGatewayClient;
import com.aidecomposer.util.Md5Utils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
public class PromptSmartCacheService {

    private final StringRedisTemplate redisTemplate;
    private final AiGatewayClient aiGatewayClient;

    @Value("${app.prompt-cache.ttl-seconds:600}")
    private long ttlSeconds;

    public PromptSmartCacheService(StringRedisTemplate redisTemplate,
                                    AiGatewayClient aiGatewayClient) {
        this.redisTemplate = redisTemplate;
        this.aiGatewayClient = aiGatewayClient;
    }

    public CachedPromptResponse generateWithCache(long tenantId,
                                                    long userId,
                                                    String prompt,
                                                    double temperature,
                                                    double topP) {
        String p = prompt == null ? "" : prompt.trim();
        String md5 = Md5Utils.md5Hex(p);

        // 为减少“仅 md5 prompt 命中但温度/TopP 不同”导致的错误缓存，这里把参数也纳入 key。
        String cacheKey = "cache:prompt:" + tenantId + ":" + md5 + ":" + temperature + ":" + topP;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return new CachedPromptResponse(cached, true);
        }

        String result = aiGatewayClient.promptTestStreamAggregate(p, temperature, topP);
        // 写回缓存：短 TTL（根据你们教学场景调整）
        redisTemplate.opsForValue().set(cacheKey, result, Duration.ofSeconds(ttlSeconds));
        return new CachedPromptResponse(result, false);
    }

    public static class CachedPromptResponse {
        private final String result;
        private final boolean cached;

        public CachedPromptResponse(String result, boolean cached) {
            this.result = result;
            this.cached = cached;
        }

        public String getResult() {
            return result;
        }

        public boolean isCached() {
            return cached;
        }
    }
}

