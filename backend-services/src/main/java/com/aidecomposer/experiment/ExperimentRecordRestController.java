package com.aidecomposer.experiment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 实验记录直连落库（便于 Prompt A/B 快照保存与回滚列表），与 Kafka 异步链路并存。
 */
@RestController
public class ExperimentRecordRestController {
    private static final Logger log = LoggerFactory.getLogger(ExperimentRecordRestController.class);

    private final ExperimentRecordService experimentRecordService;
    private final ObjectMapper objectMapper;

    public ExperimentRecordRestController(ExperimentRecordService experimentRecordService,
                                          ObjectMapper objectMapper) {
        this.experimentRecordService = experimentRecordService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/experiment/record/snapshot")
    public Map<String, Object> saveSnapshot(@Valid @RequestBody SaveExperimentSnapshotRequest req)
            throws JsonProcessingException {
        ExperimentRecord r = new ExperimentRecord();
        r.setUserId(req.getUserId());
        r.setExperimentType(
                req.getExperimentType() != null && !req.getExperimentType().isBlank()
                        ? req.getExperimentType()
                        : "PROMPT_AB");
        r.setStatus("SUCCESS");
        r.setInputPayloadJson(objectMapper.writeValueAsString(req.getPayload() != null ? req.getPayload() : Map.of()));
        LocalDateTime now = LocalDateTime.now();
        r.setGmtCreated(now);
        r.setGmtModified(now);
        experimentRecordService.save(r);
        return Map.of("code", "OK", "id", r.getId());
    }

    @GetMapping("/api/experiment/record/list")
    public List<ExperimentRecord> listByUser(
            @RequestParam("userId") long userId,
            @RequestParam(value = "experimentType", defaultValue = "PROMPT_AB") String experimentType,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        try {
            int cap = Math.min(Math.max(limit, 1), 100);
            LambdaQueryWrapper<ExperimentRecord> q = new LambdaQueryWrapper<ExperimentRecord>()
                    .eq(ExperimentRecord::getUserId, userId)
                    .eq(ExperimentRecord::getExperimentType, experimentType)
                    .orderByDesc(ExperimentRecord::getGmtCreated)
                    .last("LIMIT " + cap);
            return experimentRecordService.list(q);
        } catch (Exception ex) {
            // 兜底：数据库未就绪时，前端仍可正常进入实验页，快照列表退化为空。
            log.warn("listByUser fallback to empty list, userId={}, experimentType={}, reason={}",
                    userId, experimentType, ex.getMessage());
            return List.of();
        }
    }
}
