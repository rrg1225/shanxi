package com.aidecomposer.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 前端发送的“实验操作日志”消息（发往 Kafka）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExperimentOpLogMessage {
    private Long id; // 可选：由消费者/生产者生成
    private Long tenantId;
    private Long userId;

    // 操作类型：例如 CONNECT_RAG_NODE / ADJUST_PARAM / RUN_EXPERIMENT 等
    private String opType;

    // 操作参数（前端传入的结构化数据）
    private Map<String, Object> opPayload;

    // 方便排查
    private String clientIp;

    private Long createdAtMillis;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public Map<String, Object> getOpPayload() {
        return opPayload;
    }

    public void setOpPayload(Map<String, Object> opPayload) {
        this.opPayload = opPayload;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(Long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }
}

