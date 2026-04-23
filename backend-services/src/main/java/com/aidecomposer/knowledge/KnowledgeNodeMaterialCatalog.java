package com.aidecomposer.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 前端知识星空节点 id（如 cs-foundation）与 {@code rag_document} 的匹配规则。
 * 与 frontend-ui {@code KNOWLEDGE_MAP_NODES.materialTags / category} 对齐。
 */
public final class KnowledgeNodeMaterialCatalog {

    public record MaterialMatchRule(
            String nodeId,
            /** 与 rag_document.category 精确匹配（OR） */
            List<String> categories,
            /** 标题包含任一关键字即命中（OR，忽略大小写在 SQL 中由业务侧处理） */
            List<String> titleKeywords
    ) {
    }

    private static final Map<String, MaterialMatchRule> RULES = Map.ofEntries(
            Map.entry("cs-foundation", new MaterialMatchRule("cs-foundation",
                    List.of("计算机专业课", "基础", "general"),
                    List.of("计算机", "编程", "操作系统", "网络", "导论"))),
            Map.entry("dsa-core", new MaterialMatchRule("dsa-core",
                    List.of("计算机专业课", "算法基础"),
                    List.of("数据结构", "算法", "复杂度"))),
            Map.entry("nlp-embed", new MaterialMatchRule("nlp-embed",
                    List.of("AI 基础", "NLP", "transformer"),
                    List.of("向量", "嵌入", "Tokenizer", "词向量"))),
            Map.entry("rag-pipeline", new MaterialMatchRule("rag-pipeline",
                    List.of("RAG", "rag"),
                    List.of("RAG", "检索", "Chunk", "重排"))),
            Map.entry("vector-db", new MaterialMatchRule("vector-db",
                    List.of("工程", "向量", "rag"),
                    List.of("向量", "Milvus", "HNSW", "IVF", "索引"))),
            Map.entry("agent-orchestration", new MaterialMatchRule("agent-orchestration",
                    List.of("Agent", "agent"),
                    List.of("智能体", "Agent", "编排", "多智能体"))),
            Map.entry("course-project", new MaterialMatchRule("course-project",
                    List.of("综合", "项目"),
                    List.of("项目", "集成", "答辩", "大项目")))
    );

    private KnowledgeNodeMaterialCatalog() {
    }

    public static Optional<MaterialMatchRule> ruleFor(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(RULES.get(nodeId.trim()));
    }
}
