package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityDocument;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.DocumentVisibility;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityDocumentRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.response.DocumentDownloadResult;
import com.raitukashtam.mycommunity.response.DocumentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private CommunityDocumentRepository documentRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;
    @Mock
    private DocumentStorageService storageService;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private DocumentService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        DocumentService service = new DocumentService();
        setField(service, "documentRepository", documentRepository);
        setField(service, "communityRepository", communityRepository);
        setField(service, "communityService", communityService);
        setField(service, "storageService", storageService);
        return service;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Community community(Long id) {
        Community community = new Community();
        community.setId(id);
        community.setName("Green Valley Apartments");
        return community;
    }

    private CommunityMember member(Long id, CommunityRole role) {
        CommunityMember member = new CommunityMember();
        member.setId(id);
        member.setName("Member " + id);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        member.setCommunity(community(COMMUNITY_ID));
        return member;
    }

    private void stubActiveMember(CommunityMember m) {
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(m));
    }

    private MultipartFile pdfFile() {
        return new MockMultipartFile("file", "rules.pdf", "application/pdf", "dummy-pdf-bytes".getBytes());
    }

    private CommunityDocument document(Long id, DocumentVisibility visibility, CommunityMember uploader) {
        CommunityDocument document = new CommunityDocument();
        document.setId(id);
        document.setCommunity(community(COMMUNITY_ID));
        document.setTitle("Society Rules");
        document.setCategory("Rules");
        document.setVisibility(visibility);
        document.setS3Key("communities/1/documents/abc-rules.pdf");
        document.setContentType("application/pdf");
        document.setFileSizeBytes(15);
        document.setUploadedBy(uploader);
        return document;
    }

    @Test
    void uploadDocument_savesDocument_whenCallerIsAdmin() {
        DocumentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(documentRepository.save(any(CommunityDocument.class))).thenAnswer(invocation -> {
            CommunityDocument d = invocation.getArgument(0);
            d.setId(10L);
            return d;
        });

        DocumentResponse response = service.uploadDocument(
                COMMUNITY_ID, pdfFile(), "Society Rules", "v2", "Rules", null, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getVisibility()).isEqualTo(DocumentVisibility.ALL_MEMBERS);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        verify(storageService).upload(anyString(), eq("application/pdf"), any(byte[].class));
    }

    @Test
    void uploadDocument_honorsAdminOnlyVisibility_whenSpecified() {
        DocumentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(documentRepository.save(any(CommunityDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = service.uploadDocument(
                COMMUNITY_ID, pdfFile(), "Committee Minutes", null, "Minutes", DocumentVisibility.ADMIN_ONLY, CALLER_IDENTITY);

        assertThat(response.getVisibility()).isEqualTo(DocumentVisibility.ADMIN_ONLY);
    }

    @Test
    void uploadDocument_throwsAccessDenied_whenCallerNotAdmin() {
        DocumentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);

        assertThatThrownBy(() -> service.uploadDocument(COMMUNITY_ID, pdfFile(), "X", null, "Rules", null, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void uploadDocument_throwsBadRequest_whenFileEmpty() {
        DocumentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        MultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.uploadDocument(COMMUNITY_ID, empty, "X", null, "Rules", null, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void uploadDocument_throwsBadRequest_whenUnsupportedContentType() {
        DocumentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        MultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/x-msdownload", "x".getBytes());

        assertThatThrownBy(() -> service.uploadDocument(COMMUNITY_ID, exe, "X", null, "Rules", null, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void uploadDocument_throwsBadRequest_whenFileTooLarge() {
        DocumentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        byte[] tooBig = new byte[11 * 1024 * 1024];
        MultipartFile big = new MockMultipartFile("file", "big.pdf", "application/pdf", tooBig);

        assertThatThrownBy(() -> service.uploadDocument(COMMUNITY_ID, big, "X", null, "Rules", null, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void listDocuments_filtersOutAdminOnly_forResident() {
        DocumentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(documentRepository.findByCommunity_IdOrderByCreatedAtDesc(COMMUNITY_ID))
                .thenReturn(List.of(document(10L, DocumentVisibility.ALL_MEMBERS, admin), document(11L, DocumentVisibility.ADMIN_ONLY, admin)));

        List<DocumentResponse> result = service.listDocuments(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
    }

    @Test
    void listDocuments_showsAll_forAdmin() {
        DocumentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        when(documentRepository.findByCommunity_IdOrderByCreatedAtDesc(COMMUNITY_ID))
                .thenReturn(List.of(document(10L, DocumentVisibility.ALL_MEMBERS, admin), document(11L, DocumentVisibility.ADMIN_ONLY, admin)));

        List<DocumentResponse> result = service.listDocuments(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(2);
    }

    @Test
    void getDocument_throwsAccessDenied_whenAdminOnlyAndCallerIsResident() {
        DocumentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(documentRepository.findByIdAndCommunity_Id(11L, COMMUNITY_ID))
                .thenReturn(Optional.of(document(11L, DocumentVisibility.ADMIN_ONLY, admin)));

        assertThatThrownBy(() -> service.getDocument(COMMUNITY_ID, 11L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getDocument_throwsNotFound_whenMissing() {
        DocumentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        when(documentRepository.findByIdAndCommunity_Id(99L, COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDocument(COMMUNITY_ID, 99L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void downloadDocument_returnsContent_whenVisible() {
        DocumentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        CommunityDocument doc = document(10L, DocumentVisibility.ALL_MEMBERS, admin);
        when(documentRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(doc));
        when(storageService.download(doc.getS3Key())).thenReturn("dummy-pdf-bytes".getBytes());

        DocumentDownloadResult result = service.downloadDocument(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.filename()).isEqualTo("Society Rules");
        assertThat(new String(result.content())).isEqualTo("dummy-pdf-bytes");
    }

    @Test
    void downloadDocument_throwsAccessDenied_whenAdminOnlyAndCallerIsResident() {
        DocumentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(documentRepository.findByIdAndCommunity_Id(11L, COMMUNITY_ID))
                .thenReturn(Optional.of(document(11L, DocumentVisibility.ADMIN_ONLY, admin)));

        assertThatThrownBy(() -> service.downloadDocument(COMMUNITY_ID, 11L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(storageService, never()).download(anyString());
    }

    @Test
    void deleteDocument_deletesFromStorageAndDb_whenCallerIsAdmin() {
        DocumentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubActiveMember(admin);
        CommunityDocument doc = document(10L, DocumentVisibility.ALL_MEMBERS, admin);
        when(documentRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(doc));

        service.deleteDocument(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        verify(storageService).delete(doc.getS3Key());
        verify(documentRepository).delete(doc);
    }

    @Test
    void deleteDocument_throwsAccessDenied_whenCallerNotAdmin() {
        DocumentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        stubActiveMember(resident);

        assertThatThrownBy(() -> service.deleteDocument(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(storageService, never()).delete(anyString());
        verify(documentRepository, never()).delete(any());
    }
}
