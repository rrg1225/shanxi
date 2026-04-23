package com.aidecomposer.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "milvus")
public class MilvusGraphProperties {

    private String host;
    private int port = 19530;
    private String username = "";
    private String password = "";
    private String graphCollectionName;
    private boolean secure;
    private String metricType = "COSINE";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getGraphCollectionName() {
        return graphCollectionName;
    }

    public void setGraphCollectionName(String graphCollectionName) {
        this.graphCollectionName = graphCollectionName;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }
}
