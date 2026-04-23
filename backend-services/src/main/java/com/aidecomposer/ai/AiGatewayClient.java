package com.aidecomposer.ai;

import com.aidecomposer.ai.dto.TokenProbabilityTreeRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 调用 ai-gateway 的底层接口（流式 SSE 聚合为完整字符串返回）。
 */
@Component
public class AiGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AiGatewayCircuitState circuitState;

    @Value("${app.ai-gateway.base-url:http://127.0.0.1:8000}")
    private String baseUrl;

    @Value("${app.ai-gateway.fallback-base-url:}")
    private String fallbackBaseUrl;

    public AiGatewayClient(ObjectMapper objectMapper, AiGatewayCircuitState circuitState) {
        this.objectMapper = objectMapper;
        this.circuitState = circuitState;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public String promptTestStreamAggregate(String prompt, double temperature, double topP) {
        if (circuitState.shouldUseFallbackOrMock()) {
            String fb = tryPromptTestAggregateOnBase(trimBase(fallbackBaseUrl), prompt, temperature, topP, true);
            if (fb != null) {
                return fb;
            }
            return mockTutorAggregate(prompt);
        }
        try {
            // ai-gateway 的 PromptTestRequest max_length=20000
            if (prompt != null && prompt.length() > 19000) {
                prompt = prompt.substring(0, 19000) + "\n[truncated]";
            }

            Map<String, Object> body = new HashMap<>();
            body.put("prompt", prompt);
            body.put("temperature", temperature);
            body.put("top_p", topP);

            String json = objectMapper.writeValueAsString(body);
            byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
            String out = executePromptTestSse(trimBase(baseUrl), payloadBytes, json);
            circuitState.recordSuccess();
            return out;
        } catch (HttpTimeoutException e) {
            circuitState.recordTimeout();
            log.warn("ai-gateway prompt-test timeout (consecutive={})", circuitState.consecutiveTimeouts());
            if (circuitState.shouldUseFallbackOrMock()) {
                String fb = tryPromptTestAggregateOnBase(trimBase(fallbackBaseUrl), prompt, temperature, topP, false);
                if (fb != null) {
                    return fb;
                }
                return mockTutorAggregate(prompt);
            }
            throw new RuntimeException("call ai-gateway prompt-test failed: " + e.getMessage(), e);
        } catch (Exception e) {
            if (circuitState.shouldUseFallbackOrMock()) {
                String fb = tryPromptTestAggregateOnBase(trimBase(fallbackBaseUrl), prompt, temperature, topP, false);
                if (fb != null) {
                    return fb;
                }
                return mockTutorAggregate(prompt);
            }
            throw new RuntimeException("call ai-gateway prompt-test failed: " + e.getMessage(), e);
        }
    }

    private String tryPromptTestAggregateOnBase(String root, String prompt, double temperature, double topP, boolean alreadyCircuit) {
        if (!StringUtils.hasText(root)) {
            return null;
        }
        try {
            if (prompt != null && prompt.length() > 19000) {
                prompt = prompt.substring(0, 19000) + "\n[truncated]";
            }
            Map<String, Object> body = new HashMap<>();
            body.put("prompt", prompt);
            body.put("temperature", temperature);
            body.put("top_p", topP);
            String json = objectMapper.writeValueAsString(body);
            byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
            String out = executePromptTestSse(root, payloadBytes, json);
            circuitState.recordSuccess();
            return out;
        } catch (Exception e) {
            log.warn("prompt-test on base {} failed: {}", root, e.toString());
            return null;
        }
    }

    private String executePromptTestSse(String root, byte[] payloadBytes, String jsonForLog) throws Exception {
        URI uri = URI.create(root + "/api/ai/prompt-test");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofByteArray(payloadBytes))
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            String jsonPreview = jsonForLog.length() > 400 ? jsonForLog.substring(0, 400) + "...[truncated]" : jsonForLog;
            log.error("ai-gateway prompt-test failed, status={}, payloadPreview={}",
                    response.statusCode(), jsonPreview);
            throw new RuntimeException(
                    "ai-gateway status=" + response.statusCode() + ", errorBody=" + errorBody
            );
        }

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8)
        );

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data:")) {
                String payload = line.substring("data:".length()).trim();
                if (payload.isEmpty()) continue;

                JsonNode node = objectMapper.readTree(payload);
                if (node.has("token")) {
                    sb.append(node.get("token").asText());
                } else if (node.has("done") && node.get("done").asBoolean()) {
                    break;
                }
            } else if (line.startsWith("event:")) {
                /* error event */
            } else if (line.startsWith("error:")) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String trimBase(String base) {
        if (base == null || base.isBlank()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String mockTutorAggregate(String prompt) {
        circuitState.recordSuccess();
        return "【Mock】ai-gateway 连续超时熔断后的本地占位回复。\n\n用户问题摘要："
                + (prompt == null ? "" : prompt.substring(0, Math.min(prompt.length(), 400)));
    }

    public String promptWithInstruction(String instruction, String userContent, double temperature, double topP) {
        String mergedPrompt = (instruction == null ? "" : instruction.trim()) + "\n\n" + (userContent == null ? "" : userContent);
        return promptTestStreamAggregate(mergedPrompt.trim(), temperature, topP);
    }

    public Map<String, Object> tokenProbabilityTree(TokenProbabilityTreeRequest payload) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "prompt", payload.getPrompt(),
                    "temperature", payload.getTemperature() == null ? 0.7 : payload.getTemperature(),
                    "top_p", payload.getTopP() == null ? 0.9 : payload.getTopP()
            ));
            URI uri = URI.create(baseUrl + "/api/ai/token-probability-tree");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("ai-gateway status=" + response.statusCode());
            }
            return objectMapper.readValue(response.body(), Map.class);
        } catch (Exception e) {
            throw new RuntimeException("call ai-gateway token-probability-tree failed: " + e.getMessage(), e);
        }
    }
}

