package com.aidecomposer.rag;

import com.aidecomposer.rag.dto.DocumentProcessResponse;
import com.aidecomposer.rag.dto.RagDocumentView;
import com.aidecomposer.rag.dto.RagSearchRequest;
import com.aidecomposer.rag.dto.RagSearchResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RagOrchestrationService {

    default DocumentProcessResponse uploadAndProcess(MultipartFile file,
                                                     Long tenantId,
                                                     String visibility,
                                                     Long ownerUserId,
                                                     Long teacherUserId,
                                                     Integer chunkSize,
                                                     Integer chunkOverlap) {
        return uploadAndProcess(
                file, tenantId, visibility, ownerUserId, teacherUserId, chunkSize, chunkOverlap,
                RagUploadProgressListener.NOOP
        );
    }

    DocumentProcessResponse uploadAndProcess(MultipartFile file,
                                             Long tenantId,
                                             String visibility,
                                             Long ownerUserId,
                                             Long teacherUserId,
                                             Integer chunkSize,
                                             Integer chunkOverlap,
                                             RagUploadProgressListener progressListener);

    /**
     * 文档列表。
     *
     * @param scope 可选：{@code all} 当前用户可见（本人私有 ∪ 租户内公共）、{@code public} 仅公共、{@code private} 仅本人私有。
     *              为空时沿用 {@code kbScope}（PRIVATE/PUBLIC）的旧行为。
     */
    List<RagDocumentView> listDocuments(Long tenantId, Long ownerUserId, String kbScope, String scope);

    RagSearchResponse ragSearch(RagSearchRequest request);
}

