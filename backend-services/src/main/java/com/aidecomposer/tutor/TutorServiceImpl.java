package com.aidecomposer.tutor;

import com.aidecomposer.ai.AiGatewayClient;
import com.aidecomposer.tutor.dto.TutorHintRequest;
import com.aidecomposer.tutor.dto.TutorHintResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TutorServiceImpl implements TutorService {

    private static final Logger log = LoggerFactory.getLogger(TutorServiceImpl.class);

    private final AiGatewayClient aiGatewayClient;

    @Value("${ai.mentor.system-prompt:你是一位专业且耐心的AI导师。}")
    private String mentorSystemPrompt;

    public TutorServiceImpl(AiGatewayClient aiGatewayClient) {
        this.aiGatewayClient = aiGatewayClient;
    }

    @Override
    public TutorHintResponse generateHints(TutorHintRequest request) {
        try {
            String prompt = buildPrompt(request);
            String result = aiGatewayClient.promptWithInstruction(mentorSystemPrompt, prompt, 0.4, 0.85);

            TutorHintResponse response = new TutorHintResponse();
            response.setTips(parseTips(result));
            return response;
        } catch (Exception e) {
            log.warn("Tutor AI 调用失败（多为 ai-gateway 未启动或不可达）: {}", e.getMessage());
            TutorHintResponse fallback = new TutorHintResponse();
            fallback.setTips(List.of(
                    "当前无法连接 AI 推理服务（ai-gateway）。请先在本机启动 ai-gateway，默认地址 http://127.0.0.1:8000 ，并确认接口 /api/ai/prompt-test 可用。",
                    "若已启动仍失败，请检查 backend-services 中 app.ai-gateway.base-url 是否与网关实际地址一致。",
                    "在网关恢复前，可先使用左侧文档列表与知识图谱自学；连接正常后再次点击「发送提问」或「获取学习建议」即可。"
            ));
            return fallback;
        }
    }

    private String buildPrompt(TutorHintRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下学生上下文，给出 3-5 条简洁、可执行的引导建议。每条一行，不要写长段落。\n");
        sb.append("当前模块：").append(nullToEmpty(request.getModule())).append("\n");
        sb.append("最近报错：").append(nullToEmpty(request.getLastError())).append("\n");
        sb.append("最近操作：").append(nullToEmpty(request.getRecentAction())).append("\n");
        sb.append("学生水平：").append(nullToEmpty(request.getUserLevel())).append("\n");
        sb.append("额外上下文：").append(request.getExtraContext() == null ? "{}" : request.getExtraContext()).append("\n");
        String prompt = sb.toString();
        // ai-gateway 的 prompt-test 有 max_length=20000，这里做截断避免返回 422。
        int maxLen = 18000;
        if (prompt.length() > maxLen) {
            prompt = prompt.substring(0, maxLen) + "\n[context truncated]";
        }
        return prompt;
    }

    private List<String> parseTips(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("先确认当前步骤是否完成，再继续下一步。");
        }

        List<String> lines = Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(this::stripListPrefix)
                .filter(s -> !s.isBlank())
                .limit(5)
                .collect(Collectors.toCollection(ArrayList::new));

        if (lines.isEmpty()) {
            lines.add(raw.trim());
        }
        return lines;
    }

    private String stripListPrefix(String line) {
        return line.replaceFirst("^[-*\\d.、\\s]+", "").trim();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

