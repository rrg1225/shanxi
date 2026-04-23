package com.aidecomposer.knowledge;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.stereotype.Component;

/**
 * 懒连接 Milvus，避免本地开发未配向量库时拖垮整个 Spring 启动。
 */
@Component
public class KnowledgeGraphMilvusBridge {

    private final MilvusGraphProperties props;
    private volatile MilvusServiceClient client;

    public KnowledgeGraphMilvusBridge(MilvusGraphProperties props) {
        this.props = props;
    }

    public MilvusServiceClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    ConnectParam.Builder b = ConnectParam.newBuilder()
                            .withHost(props.getHost())
                            .withPort(props.getPort())
                            .withAuthorization(props.getUsername(), props.getPassword());
                    if (props.isSecure()) {
                        b.withSecure(true);
                    }
                    client = new MilvusServiceClient(b.build());
                }
            }
        }
        return client;
    }
}
