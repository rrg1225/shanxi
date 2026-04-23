package com.aidecomposer.ratelimit;

/**
 * Token Bucket Lua 脚本（Redis）。
 *
 * 返回：
 * - 1: 允许
 * - 0: 不允许
 *
 * KEYS[1] = bucketKey
 * ARGV[1] = nowMillis
 * ARGV[2] = capacity
 * ARGV[3] = refillRatePerSecond
 * ARGV[4] = permits
 * ARGV[5] = stateTtlSeconds
 */
public class RateLimitLua {

    // tokens + last_ts 两个字段放在 hash 中
    public static final String SCRIPT =
            "local bucketKey = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local capacity = tonumber(ARGV[2])\n" +
            "local refillRate = tonumber(ARGV[3])\n" +
            "local permits = tonumber(ARGV[4])\n" +
            "local ttlSeconds = tonumber(ARGV[5])\n" +
            "\n" +
            "local tokens = tonumber(redis.call('hget', bucketKey, 'tokens'))\n" +
            "local lastTs = tonumber(redis.call('hget', bucketKey, 'ts'))\n" +
            "if tokens == nil or lastTs == nil then\n" +
            "  tokens = capacity\n" +
            "  lastTs = now\n" +
            "end\n" +
            "\n" +
            "local deltaMillis = now - lastTs\n" +
            "if deltaMillis < 0 then\n" +
            "  deltaMillis = 0\n" +
            "end\n" +
            "local deltaSeconds = deltaMillis / 1000\n" +
            "local refill = deltaSeconds * refillRate\n" +
            "tokens = tokens + refill\n" +
            "if tokens > capacity then\n" +
            "  tokens = capacity\n" +
            "end\n" +
            "\n" +
            "if tokens < permits then\n" +
            "  -- 不允许：刷新 lastTs 以避免恶意回拨造成无限补给\n" +
            "  redis.call('hset', bucketKey, 'tokens', tokens)\n" +
            "  redis.call('hset', bucketKey, 'ts', now)\n" +
            "  redis.call('expire', bucketKey, ttlSeconds)\n" +
            "  return 0\n" +
            "end\n" +
            "tokens = tokens - permits\n" +
            "redis.call('hset', bucketKey, 'tokens', tokens)\n" +
            "redis.call('hset', bucketKey, 'ts', now)\n" +
            "redis.call('expire', bucketKey, ttlSeconds)\n" +
            "return 1\n";
}

