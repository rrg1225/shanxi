package com.aidecomposer.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 令牌桶限流注解（Lua 脚本实现）。
 *
 * keySpel 支持 SpEL，例如："#tenantId + ':' + #userId"
 * - 需要方法入参名：建议编译时启用 -parameters（pom 已开启）
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RateLimit {
    /**
     * key 前缀
     */
    String keyPrefix() default "ratelimit:llm";

    /**
     * 用于构建限流 key 的 SpEL 表达式（可为空）
     */
    String keySpel() default "";

    /**
     * 桶容量（令牌上限）
     */
    long capacity() default 10;

    /**
     * 令牌补充速率（tokens / second）
     */
    long refillRate() default 5;

    /**
     * 每次请求消耗的令牌数
     */
    long permits() default 1;

    /**
     * 状态在 Redis 中保存的最长秒数（避免无限增长）
     */
    long stateTtlSeconds() default 3600;
}

