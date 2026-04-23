package com.aidecomposer.agentarena;

import com.aidecomposer.agentarena.dto.AgentArenaMessage;
import com.aidecomposer.agentarena.dto.AgentArenaRunRequest;
import com.aidecomposer.ai.AiGatewayClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AgentArenaServiceImpl implements AgentArenaService {

    private final AiGatewayClient aiGatewayClient;
    private final AgentArenaBroadcastService broadcastService;

    public AgentArenaServiceImpl(AiGatewayClient aiGatewayClient,
                                 AgentArenaBroadcastService broadcastService) {
        this.aiGatewayClient = aiGatewayClient;
        this.broadcastService = broadcastService;
    }

    @Override
    public void runDemoDiscussion(AgentArenaRunRequest request) {
        CompletableFuture.runAsync(() -> {
            String topic = request == null || request.getTopic() == null || request.getTopic().isBlank()
                    ? "请围绕一个RAG实验流程进行协作分析"
                    : request.getTopic();

            sendRoleMessage("analyst",
                    "你是数据分析师。请围绕这个主题给出 2-4 句分析观察，语言简洁：\n" + topic);
            sleepQuietly(300);

            sendRoleMessage("reviewer",
                    "你是审查员。请指出该主题下设计中最值得警惕的风险、缺陷或验证点，2-4 句：\n" + topic);
            sleepQuietly(300);

            sendRoleMessage("writer",
                    "你是撰写者。请基于前述分析，输出一段适合展示在教学平台中的总结说明，2-4 句：\n" + topic);
        });
    }

    private void sendRoleMessage(String role, String prompt) {
        String instruction = switch (role) {
            case "analyst" -> "你是严谨的数据分析师，擅长概括现象、给出结构化观察。";
            case "reviewer" -> "你是审查员，擅长发现风险、漏洞、验证盲区。";
            case "writer" -> "你是撰写者，擅长把复杂信息整理成适合教学平台展示的总结。";
            default -> "你是协作Agent。";
        };
        String content = aiGatewayClient.promptWithInstruction(instruction, prompt, 0.6, 0.9);
        AgentArenaMessage message = new AgentArenaMessage();
        message.setRole(role);
        message.setContent(content);
        message.setEvent("thinking");
        broadcastService.broadcast(message);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

