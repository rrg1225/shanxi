package com.aidecomposer.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 默认每天凌晨 2 点（Asia/Shanghai）全量重建知识图谱边。
 * <p>
 * 关闭：app.knowledge-graph.enabled=false
 */
@Component
@ConditionalOnProperty(prefix = "app.knowledge-graph", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeGraphScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphScheduledTask.class);

    private final KnowledgeGraphBuildService buildService;

    public KnowledgeGraphScheduledTask(KnowledgeGraphBuildService buildService) {
        this.buildService = buildService;
    }

    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void nightlyRebuildKnowledgeGraph() {
        log.info("Knowledge graph nightly job started");
        try {
            buildService.rebuildAllEdges();
        } catch (Exception e) {
            log.error("Knowledge graph nightly job failed", e);
        }
        log.info("Knowledge graph nightly job finished");
    }
}
