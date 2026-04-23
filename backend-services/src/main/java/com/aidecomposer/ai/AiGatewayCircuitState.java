package com.aidecomposer.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简易熔断：统计对 ai-gateway 的<strong>连续超时</strong>次数，达到阈值后由调用方改走备用地址或 Mock。
 */
@Component
public class AiGatewayCircuitState {

    private final AtomicInteger consecutiveTimeouts = new AtomicInteger(0);

    @Value("${app.ai-gateway.circuit.timeout-failure-threshold:3}")
    private int timeoutFailureThreshold;

    public int getTimeoutFailureThreshold() {
        return timeoutFailureThreshold;
    }

    public int consecutiveTimeouts() {
        return consecutiveTimeouts.get();
    }

    public boolean shouldUseFallbackOrMock() {
        return consecutiveTimeouts.get() >= timeoutFailureThreshold;
    }

    public void recordTimeout() {
        consecutiveTimeouts.incrementAndGet();
    }

    public void recordSuccess() {
        consecutiveTimeouts.set(0);
    }
}
