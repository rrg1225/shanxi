package com.aidecomposer.kafka;

import com.aidecomposer.experiment.ExperimentRecord;
import com.aidecomposer.experiment.ExperimentRecordService;
import com.aidecomposer.util.SnowflakeIdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExperimentLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExperimentLogConsumer.class);

    private final ObjectMapper objectMapper;
    private final ExperimentRecordService experimentRecordService;
    private final SnowflakeIdWorker snowflakeIdWorker;

    @Value("${app.kafka.experiment-log-topic:experiment-log-topic}")
    private String topic;

    public ExperimentLogConsumer(ObjectMapper objectMapper,
                                   ExperimentRecordService experimentRecordService,
                                   @Value("${app.snowflake.worker-id:1}") long workerId,
                                   @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        this.objectMapper = objectMapper;
        this.experimentRecordService = experimentRecordService;
        this.snowflakeIdWorker = new SnowflakeIdWorker(workerId, datacenterId, 1577836800000L);
    }

    @KafkaListener(
            topics = "${app.kafka.experiment-log-topic:experiment-log-topic}",
            groupId = "${app.kafka.experiment-log-group:experiment-log-group}",
            containerFactory = "experimentKafkaListenerContainerFactory"
    )
    public void consumeBatch(List<ConsumerRecord<String, String>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        List<ExperimentRecord> batch = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> rec : records) {
            String value = rec.value();
            try {
                ExperimentOpLogMessage msg = objectMapper.readValue(value, ExperimentOpLogMessage.class);
                batch.add(toExperimentRecord(msg));
            } catch (Exception e) {
                // 单条解析失败：跳过，避免整个 batch 回滚
                log.warn("Skip invalid experiment log. topic={}, partition={}, offset={}, error={}",
                        rec.topic(), rec.partition(), rec.offset(), e.toString());
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        try {
            // 批量写入 MySQL
            experimentRecordService.saveBatch(batch, 200);
        } catch (Exception e) {
            // 兜底：仅记录错误，不再抛出，避免数据库异常导致 Kafka 消费死循环重试
            log.error("Batch persist experiment records failed, skip this batch. topic={}, size={}, error={}", topic, batch.size(), e.toString());
        }
    }

    private ExperimentRecord toExperimentRecord(ExperimentOpLogMessage msg) throws JsonProcessingException {
        ExperimentRecord record = new ExperimentRecord();

        if (msg.getId() != null) {
            record.setId(msg.getId());
        } else {
            record.setId(snowflakeIdWorker.nextId());
        }

        record.setUserId(msg.getUserId());
        record.setExperimentType(msg.getOpType());
        record.setStatus("SUCCESS");

        Object payload = msg.getOpPayload();
        String payloadJson;
        if (payload == null) {
            payloadJson = null;
        } else if (payload instanceof String s) {
            // 如果前端已经传了 JSON 字符串，则直接落库
            String trimmed = s.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                payloadJson = trimmed;
            } else {
                payloadJson = objectMapper.writeValueAsString(payload);
            }
        } else {
            payloadJson = objectMapper.writeValueAsString(payload);
        }
        record.setInputPayloadJson(payloadJson);

        long createdAtMillis = msg.getCreatedAtMillis() == null ? Instant.now().toEpochMilli() : msg.getCreatedAtMillis();
        LocalDateTime ts = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAtMillis), java.time.ZoneId.systemDefault());
        record.setGmtCreated(ts);
        record.setGmtModified(ts);
        return record;
    }
}

