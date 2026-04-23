package com.aidecomposer.ratelimit;

/**
 * 限流异常：返回 HTTP 429。
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}

