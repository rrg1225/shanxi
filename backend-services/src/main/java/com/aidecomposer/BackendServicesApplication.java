package com.aidecomposer;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// 仅扫描带 @Mapper 注解的接口，避免 Service 接口被误当成 mapper
@MapperScan(basePackages = "com.aidecomposer", annotationClass = Mapper.class)
public class BackendServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendServicesApplication.class, args);
    }
}

