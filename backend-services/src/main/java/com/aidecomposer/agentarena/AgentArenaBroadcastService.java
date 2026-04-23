package com.aidecomposer.agentarena;

import com.aidecomposer.agentarena.dto.AgentArenaMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

@Service
public class AgentArenaBroadcastService {

    private final AgentArenaWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public AgentArenaBroadcastService(AgentArenaWebSocketHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    public void broadcast(AgentArenaMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            handler.broadcast(new TextMessage(json));
        } catch (Exception e) {
            throw new RuntimeException("broadcast agent arena message failed", e);
        }
    }
}

