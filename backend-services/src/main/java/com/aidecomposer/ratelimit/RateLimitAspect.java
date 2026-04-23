package com.aidecomposer.ratelimit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.currentTimeMillis;

/**
 * AOP 令牌桶限流：
 * - Lua 返回 1：允许
 * - Lua 返回 0：拒绝（HTTP 429）
 */
@Aspect
@Component
public class RateLimitAspect {
    private final StringRedisTemplate redisTemplate;
    private final ExpressionParser spelParser = new SpelExpressionParser();

    private final DefaultRedisScript<Long> script;

    public RateLimitAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(RateLimitLua.SCRIPT);
        this.script.setResultType(Long.class);
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        String bucketKey = buildBucketKey(rateLimit, method, signature.getParameterNames(), joinPoint.getArgs());

        long nowMillis = currentTimeMillis();
        long capacity = rateLimit.capacity();
        long refillRate = rateLimit.refillRate();
        long permits = rateLimit.permits();
        long stateTtlSeconds = rateLimit.stateTtlSeconds();

        Long allowed = redisTemplate.execute(
                script,
                java.util.Collections.singletonList(bucketKey),
                String.valueOf(nowMillis),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(permits),
                String.valueOf(stateTtlSeconds)
        );

        if (allowed == null || allowed != 1L) {
            throw new RateLimitExceededException("Too many requests: " + bucketKey);
        }

        return joinPoint.proceed();
    }

    private String buildBucketKey(RateLimit rateLimit, Method method, String[] paramNames, Object[] args) {
        String prefix = rateLimit.keyPrefix();
        String keySpel = rateLimit.keySpel();

        if (!StringUtils.hasText(keySpel)) {
            return prefix + ":" + method.getName();
        }

        // 绑定方法参数到 SpEL 上下文
        Map<String, Object> vars = new HashMap<>();
        if (paramNames != null && paramNames.length == args.length) {
            for (int i = 0; i < paramNames.length; i++) {
                vars.put(paramNames[i], args[i]);
            }
        } else {
            //兜底：没有参数名时，按 index 暴露 p0/p1...
            for (int i = 0; i < args.length; i++) {
                vars.put("p" + i, args[i]);
            }
        }

        StandardEvaluationContext ctx = new StandardEvaluationContext();
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            ctx.setVariable(e.getKey(), e.getValue());
        }

        Expression expr = spelParser.parseExpression(keySpel);
        Object value = expr.getValue(ctx);
        String suffix = value == null ? "null" : value.toString();
        return prefix + ":" + suffix;
    }
}

