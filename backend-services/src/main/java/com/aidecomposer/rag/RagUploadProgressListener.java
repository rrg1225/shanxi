package com.aidecomposer.rag;

/**
 * RAG 文档上传各阶段进度（供 SSE 推送）。
 */
@FunctionalInterface
public interface RagUploadProgressListener {

    RagUploadProgressListener NOOP = (percent, phase) -> { };

    /**
     * @param percent 0–100
     * @param phase   机器可读阶段标识，如 accepted / gateway / persist
     */
    void onProgress(int percent, String phase);
}
