package com.aidecomposer.rag;

import com.aidecomposer.ai.AiGatewayCircuitState;
import com.aidecomposer.rag.dto.DocumentProcessResponse;
import com.aidecomposer.rag.dto.RagSearchRequest;
import com.aidecomposer.rag.dto.RagSearchResponse;
import com.aidecomposer.util.SnowflakeIdWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 调用 ai-gateway 的 RAG 接口；带超时 RestTemplate、连续超时熔断、备用 base-url 与本地 Mock。
 */
@Component
public class AiGatewayRagClient {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayRagClient.class);

    private final RestTemplate restTemplate;
    private final AiGatewayCircuitState circuitState;
    private final SnowflakeIdWorker snowflakeIdWorker;

    @Value("${app.ai-gateway.base-url:http://127.0.0.1:8000}")
    private String aiGatewayBaseUrl;

    /** 备用网关；空则熔断后仅 Mock */
    @Value("${app.ai-gateway.fallback-base-url:}")
    private String fallbackBaseUrl;

    public AiGatewayRagClient(
            @Qualifier("aiGatewayRestTemplate") RestTemplate restTemplate,
            AiGatewayCircuitState circuitState,
            SnowflakeIdWorker snowflakeIdWorker) {
        this.restTemplate = restTemplate;
        this.circuitState = circuitState;
        this.snowflakeIdWorker = snowflakeIdWorker;
    }

    public DocumentProcessResponse processDocument(MultipartFile file,
                                                   Long tenantId,
                                                   String visibility,
                                                   Long ownerUserId,
                                                   Long teacherUserId,
                                                   Integer chunkSize,
                                                   Integer chunkOverlap) {
        if (circuitState.shouldUseFallbackOrMock()) {
            DocumentProcessResponse r = tryFallbackThenMockDocument(
                    file, tenantId, visibility, ownerUserId, teacherUserId, chunkSize, chunkOverlap);
            if (r != null) {
                return r;
            }
        }

        try {
            DocumentProcessResponse r = exchangeDocument(
                    aiGatewayBaseUrl, file, tenantId, visibility, ownerUserId, teacherUserId, chunkSize, chunkOverlap);
            circuitState.recordSuccess();
            return r;
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                circuitState.recordTimeout();
                log.warn("ai-gateway document-process timeout (consecutive={}). {}",
                        circuitState.consecutiveTimeouts(), e.toString());
            } else {
                log.warn("ai-gateway document-process I/O error: {}", e.toString());
            }
            if (circuitState.shouldUseFallbackOrMock()) {
                DocumentProcessResponse r = tryFallbackThenMockDocument(
                        file, tenantId, visibility, ownerUserId, teacherUserId, chunkSize, chunkOverlap);
                if (r != null) {
                    return r;
                }
            }
            throw new RuntimeException("call ai-gateway document-process failed", e);
        } catch (RestClientException e) {
            if (circuitState.shouldUseFallbackOrMock()) {
                DocumentProcessResponse r = tryFallbackThenMockDocument(
                        file, tenantId, visibility, ownerUserId, teacherUserId, chunkSize, chunkOverlap);
                if (r != null) {
                    return r;
                }
            }
            throw new RuntimeException("call ai-gateway document-process failed", e);
        } catch (Exception e) {
            throw new RuntimeException("call ai-gateway document-process failed", e);
        }
    }

    /**
     * 达到超时阈值后：先试备用 URL，再返回 Mock，并 {@link AiGatewayCircuitState#recordSuccess()} 以便恢复主链路。
     */
    private DocumentProcessResponse tryFallbackThenMockDocument(MultipartFile file,
                                                                Long tenantId,
                                                                String visibility,
                                                                Long ownerUserId,
                                                                Long teacherUserId,
                                                                Integer chunkSize,
                                                                Integer chunkOverlap) {
        if (StringUtils.hasText(fallbackBaseUrl)) {
            try {
                DocumentProcessResponse r = exchangeDocument(
                        fallbackBaseUrl.trim(), file, tenantId, visibility, ownerUserId, teacherUserId, chunkSize, chunkOverlap);
                circuitState.recordSuccess();
                log.info("document-process succeeded via fallback gateway");
                return r;
            } catch (Exception e) {
                log.warn("fallback ai-gateway document-process failed: {}", e.toString());
            }
        }
        log.warn("returning mock document-process after circuit threshold (no fallback or fallback failed)");
        DocumentProcessResponse m = buildMockDocumentProcess(file);
        circuitState.recordSuccess();
        return m;
    }

    private DocumentProcessResponse exchangeDocument(String base,
                                                     MultipartFile file,
                                                     Long tenantId,
                                                     String visibility,
                                                     Long ownerUserId,
                                                     Long teacherUserId,
                                                     Integer chunkSize,
                                                     Integer chunkOverlap) throws Exception {
        String root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentDispositionFormData("file", file.getOriginalFilename());
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        body.add("tenant_id", tenantId);
        body.add("visibility", visibility);
        body.add("owner_user_id", ownerUserId);
        if (teacherUserId != null) {
            body.add("teacher_user_id", teacherUserId);
        }
        body.add("chunk_size", chunkSize);
        body.add("chunk_overlap", chunkOverlap);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<DocumentProcessResponse> response = restTemplate.exchange(
                root + "/api/ai/document-process",
                HttpMethod.POST,
                request,
                DocumentProcessResponse.class
        );
        return response.getBody();
    }

    private DocumentProcessResponse buildMockDocumentProcess(MultipartFile file) {
        DocumentProcessResponse r = new DocumentProcessResponse();
        long docId = snowflakeIdWorker.nextId();
        r.setDocumentId(docId);
        r.setChunkCount(1);
        r.setEmbedDim(3);
        r.setEmbeddingModel("mock-offline");
        String preview = "【Mock】网关连续超时熔断或备用不可用。文件名："
                + (file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename());
        try {
            byte[] b = file.getBytes();
            if (b.length > 0) {
                String head = new String(b, 0, Math.min(b.length, 400), StandardCharsets.UTF_8).replace('\n', ' ');
                preview = preview + " | 内容节选：" + head;
            }
        } catch (Exception ignored) {
            /* keep preview */
        }
        DocumentProcessResponse.DocumentChunkPreview c = new DocumentProcessResponse.DocumentChunkPreview();
        c.setChunkId(snowflakeIdWorker.nextId());
        c.setChunkIndex(0);
        c.setTextPreview(preview.length() > 500 ? preview.substring(0, 500) : preview);
        c.setCoords(List.of(0.0, 0.0, 0.0));
        List<DocumentProcessResponse.DocumentChunkPreview> chunks = new ArrayList<>();
        chunks.add(c);
        r.setChunks(chunks);
        return r;
    }

    public RagSearchResponse ragSearch(RagSearchRequest requestBody) {
        if (circuitState.shouldUseFallbackOrMock()) {
            RagSearchResponse r = tryFallbackThenMockSearch(requestBody);
            if (r != null) {
                return r;
            }
        }
        try {
            RagSearchResponse r = exchangeSearch(aiGatewayBaseUrl, requestBody);
            circuitState.recordSuccess();
            return r;
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                circuitState.recordTimeout();
                log.warn("ai-gateway rag-search timeout (consecutive={})", circuitState.consecutiveTimeouts());
            }
            if (circuitState.shouldUseFallbackOrMock()) {
                RagSearchResponse r = tryFallbackThenMockSearch(requestBody);
                if (r != null) {
                    return r;
                }
            }
            throw new RuntimeException("call ai-gateway rag-search failed", e);
        } catch (RestClientException e) {
            if (circuitState.shouldUseFallbackOrMock()) {
                RagSearchResponse r = tryFallbackThenMockSearch(requestBody);
                if (r != null) {
                    return r;
                }
            }
            throw new RuntimeException("call ai-gateway rag-search failed", e);
        }
    }

    private RagSearchResponse tryFallbackThenMockSearch(RagSearchRequest requestBody) {
        if (StringUtils.hasText(fallbackBaseUrl)) {
            try {
                RagSearchResponse r = exchangeSearch(fallbackBaseUrl.trim(), requestBody);
                circuitState.recordSuccess();
                return r;
            } catch (Exception e) {
                log.warn("fallback rag-search failed: {}", e.toString());
            }
        }
        RagSearchResponse empty = new RagSearchResponse();
        empty.setQuery(requestBody.getQuery());
        empty.setTopK(requestBody.getTopK() == null ? 0 : requestBody.getTopK());
        empty.setResults(List.of());
        circuitState.recordSuccess();
        return empty;
    }

    private RagSearchResponse exchangeSearch(String base, RagSearchRequest requestBody) {
        String root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RagSearchRequest> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<RagSearchResponse> response = restTemplate.exchange(
                root + "/api/ai/rag-search",
                HttpMethod.POST,
                request,
                RagSearchResponse.class
        );
        return response.getBody();
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        String m = e.getMessage();
        return m != null && m.toLowerCase().contains("timed out");
    }
}
