package com.aidecomposer.config;

import com.aidecomposer.util.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnowflakeConfig {

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker(
            @Value("${app.snowflake.worker-id:1}") long workerId,
            @Value("${app.snowflake.datacenter-id:1}") long datacenterId) {
        return new SnowflakeIdWorker(workerId, datacenterId, 1577836800000L);
    }
}
