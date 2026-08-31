package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityDocument;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.DocumentVisibility;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityDocumentRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.response.DocumentDownloadResult;
import com.raitukashtam.mycommunity.response.DocumentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Metadata lives in Postgres (CommunityDocument); the actual file bytes
 * live in S3 (DocumentStorageService), keyed by
 * "communities/{communityId}/documents/{uuid}-{sanitizedFilename}".
 * ADMIN uploads/deletes -- matches the management-enters,
 * residents-view shape Announcements already established. Visibility
 * (ALL_MEMBERS vs ADMIN_ONLY) is enforced on every read path: list
 * filters out what the caller can't see, get/download 403 outright.
 */
@Service
@Slf4j
public class DocumentService {
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired
    private CommunityDocumentRepository documentRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private DocumentStorageService storageService;

    @Transactional
    public DocumentResponse uploadDocument(Long communityId, MultipartFile file, String title, String description,
                                            String category, DocumentVisibility visibility, String callerIdentityId) {
        CommunityMember admin = communityService.requireActiveAdmin(communityId, callerIdentityId);
        validateUpload(file, title, category);

        String key = "communities/" + communityId + "/documents/" + UUID.randomUUID() + "-" + sanitizeFilename(file.getOriginalFilename());
        storageService.upload(key, file.getContentType(), readBytes(file));

        Community community = communityRepository.getReferenceById(communityId);
        CommunityDocument document = new CommunityDocument();
        document.setCommunity(community);
        document.setTitle(title.trim());
        document.setDescription(description != null ? description.trim() : null);
        document.setCategory(category.trim());
        document.setVisibility(visibility != null ? visibility : DocumentVisibility.ALL_MEMBERS);
        document.setS3Key(key);
        document.setContentType(file.getContentType());
        document.setFileSizeBytes(file.getSize());
        document.setUploadedBy(admin);
        CommunityDocument saved = documentRepository.save(document);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(Long communityId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        return documentRepository.findByCommunity_IdOrderByCreatedAtDesc(communityId).stream()
                .filter(doc -> isVisibleTo(doc, caller))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long communityId, Long documentId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        CommunityDocument document = requireDocument(communityId, documentId);
        requireVisible(document, caller);
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public DocumentDownloadResult downloadDocument(Long communityId, Long documentId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        CommunityDocument document = requireDocument(communityId, documentId);
        requireVisible(document, caller);

        byte[] content = storageService.download(document.getS3Key());
        return new DocumentDownloadResult(content, document.getContentType(), document.getTitle());
    }

    @Transactional
    public void deleteDocument(Long communityId, Long documentId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        CommunityDocument document = requireDocument(communityId, documentId);
        storageService.delete(document.getS3Key());
        documentRepository.delete(document);
    }

    private void validateUpload(MultipartFile file, String title, String category) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds the 10 MB size limit");
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type: " + file.getContentType());
        }
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }
        if (category == null || category.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is required");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        return originalFilename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private boolean isVisibleTo(CommunityDocument document, CommunityMember caller) {
        return document.getVisibility() == DocumentVisibility.ALL_MEMBERS || caller.getRole() == CommunityRole.ADMIN;
    }

    private void requireVisible(CommunityDocument document, CommunityMember caller) {
        if (!isVisibleTo(document, caller)) {
            throw new AccessDeniedException("Not authorized to view this document");
        }
    }

    private CommunityDocument requireDocument(Long communityId, Long documentId) {
        return documentRepository.findByIdAndCommunity_Id(documentId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));
    }

    private DocumentResponse toResponse(CommunityDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getCommunity().getId(),
                document.getTitle(),
                document.getDescription(),
                document.getCategory(),
                document.getVisibility(),
                document.getContentType(),
                document.getFileSizeBytes(),
                document.getUploadedBy().getId(),
                document.getUploadedBy().getName(),
                document.getCreatedAt());
    }
}
