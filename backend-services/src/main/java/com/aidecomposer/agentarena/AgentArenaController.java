package com.aidecomposer.agentarena;

import com.aidecomposer.agentarena.dto.AgentArenaRunRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent-arena")
public class AgentArenaController {

    private final AgentArenaService agentArenaService;

    public AgentArenaController(AgentArenaService agentArenaService) {
        this.agentArenaService = agentArenaService;
    }

    @PostMapping("/run-demo")
    public Map<String, Object> runDemo(@RequestBody(required = false) AgentArenaRunRequest request) {
        agentArenaService.runDemoDiscussion(request == null ? new AgentArenaRunRequest() : request);
        return Map.of("code", "OK", "message", "agent arena demo started");
    }
}

