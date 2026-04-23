package com.aidecomposer.experiment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * ExperimentRecord：把 Kafka 中的“实验操作日志”落盘到 MySQL。
 */
@TableName("experiment_record")
public class ExperimentRecord {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("experiment_type")
    private String experimentType;

    // JSON：前端传入的参数/上下文（CONNECT_RAG_NODE / ADJUST_PARAM 等）
    @TableField("input_payload")
    private String inputPayloadJson;

    @TableField("status")
    private String status;

    @TableField("gmt_created")
    private LocalDateTime gmtCreated;

    @TableField("gmt_modified")
    private LocalDateTime gmtModified;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getExperimentType() {
        return experimentType;
    }

    public void setExperimentType(String experimentType) {
        this.experimentType = experimentType;
    }

    public String getInputPayloadJson() {
        return inputPayloadJson;
    }

    public void setInputPayloadJson(String inputPayloadJson) {
        this.inputPayloadJson = inputPayloadJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getGmtCreated() {
        return gmtCreated;
    }

    public void setGmtCreated(LocalDateTime gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    public LocalDateTime getGmtModified() {
        return gmtModified;
    }

    public void setGmtModified(LocalDateTime gmtModified) {
        this.gmtModified = gmtModified;
    }
}

