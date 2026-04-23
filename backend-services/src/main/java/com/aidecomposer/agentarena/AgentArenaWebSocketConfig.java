package com.aidecomposer.agentarena;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AgentArenaWebSocketConfig implements WebSocketConfigurer {

    @Value("${app.ws.agent-arena-path:/ws/agent-arena}")
    private String agentArenaPath;

    @Bean
    public AgentArenaWebSocketHandler agentArenaWebSocketHandler() {
        return new AgentArenaWebSocketHandler();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentArenaWebSocketHandler(), agentArenaPath)
                .setAllowedOrigins("*");
    }
}

