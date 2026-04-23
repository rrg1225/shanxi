package com.aidecomposer.kafka;

import com.aidecomposer.util.SnowflakeIdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * Kafka Producer：把前端实验操作日志发送到 Kafka。
 */
@Service
public class ExperimentLogProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;

    @Value("${app.kafka.experiment-log-topic:experiment-log-topic}")
    private String topic;

    public ExperimentLogProducer(KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper,
                                  @Value("${app.snowflake.worker-id:1}") long workerId,
                                  @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;

        // epoch：2020-01-01
        long epochMilli = 1577836800000L;
        this.snowflakeIdWorker = new SnowflakeIdWorker(workerId, datacenterId, epochMilli);
    }

    public void send(ExperimentOpLogMessage msg) {
        try {
            if (msg.getId() == null) {
                msg.setId(snowflakeIdWorker.nextId());
            }
            if (msg.getCreatedAtMillis() == null) {
                msg.setCreatedAtMillis(Instant.now().toEpochMilli());
            }

            String json = objectMapper.writeValueAsString(msg);
            // key：按 tenantId/userId 分桶，方便后续按用户顺序消费（可选）
            String key = Objects.toString(msg.getTenantId(), "0") + ":" + Objects.toString(msg.getUserId(), "0");
            kafkaTemplate.send(topic, key, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("encode experiment log failed", e);
        }
    }
}

