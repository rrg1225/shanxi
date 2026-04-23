package com.aidecomposer.ai;

import com.aidecomposer.ai.dto.TokenProbabilityTreeRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiGatewayClient aiGatewayClient;

    public AiController(AiGatewayClient aiGatewayClient) {
        this.aiGatewayClient = aiGatewayClient;
    }

    @PostMapping("/token-probability-tree")
    public Map<String, Object> tokenProbabilityTree(@RequestBody TokenProbabilityTreeRequest request) {
        return aiGatewayClient.tokenProbabilityTree(request);
    }
}

