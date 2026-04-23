package com.aidecomposer.kafka;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前端把“实验操作”发到这里 -> Producer -> Kafka -> Consumer -> MySQL。
 */
@RestController
public class ExperimentLogController {

    private final ExperimentLogProducer producer;

    public ExperimentLogController(ExperimentLogProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/api/experiment/log")
    public Map<String, Object> postLog(HttpServletRequest request, @Valid @RequestBody ExperimentOpLogMessage msg) {
        // 兜底：补 clientIp
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        msg.setClientIp(ip);

        producer.send(msg);
        return Map.of("code", "OK");
    }
}

