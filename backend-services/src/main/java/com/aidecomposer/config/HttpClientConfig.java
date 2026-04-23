package com.aidecomposer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 调用 ai-gateway（RAG 文档处理等）专用：可配置连接/读取超时，避免默认无限等待。
     */
    @Bean(name = "aiGatewayRestTemplate")
    public RestTemplate aiGatewayRestTemplate(
            @Value("${app.ai-gateway.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.ai-gateway.read-timeout-ms:300000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}

