package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Announcement;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.AnnouncementRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.AnnouncementRequest;
import com.raitukashtam.mycommunity.response.AnnouncementResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository announcementRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;
    @Mock
    private NotificationService notificationService;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private AnnouncementService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        AnnouncementService service = new AnnouncementService();
        setField(service, "announcementRepository", announcementRepository);
        setField(service, "communityRepository", communityRepository);
        setField(service, "communityMemberRepository", communityMemberRepository);
        setField(service, "communityService", communityService);
        setField(service, "notificationService", notificationService);
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

    private CommunityMember member(CommunityRole role) {
        CommunityMember member = new CommunityMember();
        member.setId(5L);
        member.setName("Admin One");
        member.setRole(role);
        member.setCommunity(community(COMMUNITY_ID));
        return member;
    }

    @Test
    void createAnnouncement_savesAnnouncement_whenCallerIsActiveAdmin() {
        AnnouncementService service = buildService();
        CommunityMember admin = member(CommunityRole.ADMIN);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(communityMemberRepository.findByCommunity_IdAndStatus(COMMUNITY_ID, MemberStatus.ACTIVE)).thenReturn(List.of(admin));
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(invocation -> {
            Announcement a = invocation.getArgument(0);
            a.setId(10L);
            return a;
        });

        AnnouncementRequest request = new AnnouncementRequest();
        request.setTitle("Water shutdown");
        request.setBody("Water will be off 10am-2pm tomorrow for tank cleaning.");

        AnnouncementResponse response = service.createAnnouncement(COMMUNITY_ID, request, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getTitle()).isEqualTo("Water shutdown");
        assertThat(response.getPostedByMemberId()).isEqualTo(5L);
        assertThat(response.getPostedByName()).isEqualTo("Admin One");
    }

    @Test
    void createAnnouncement_throwsAccessDenied_whenCallerNotAdmin() {
        AnnouncementService service = buildService();
        CommunityMember resident = member(CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        AnnouncementRequest request = new AnnouncementRequest();
        request.setTitle("Water shutdown");
        request.setBody("Body");

        assertThatThrownBy(() -> service.createAnnouncement(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void listAnnouncements_returnsOrderedList_whenCallerIsActiveMember() {
        AnnouncementService service = buildService();
        CommunityMember resident = member(CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        Announcement announcement = new Announcement();
        announcement.setId(10L);
        announcement.setCommunity(community(COMMUNITY_ID));
        announcement.setTitle("Water shutdown");
        announcement.setBody("Body");
        announcement.setPostedBy(member(CommunityRole.ADMIN));
        when(announcementRepository.findByCommunity_IdOrderByCreatedAtDesc(COMMUNITY_ID))
                .thenReturn(List.of(announcement));

        List<AnnouncementResponse> result = service.listAnnouncements(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Water shutdown");
    }

    @Test
    void listAnnouncements_throwsAccessDenied_whenCallerNotMember() {
        AnnouncementService service = buildService();
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listAnnouncements(COMMUNITY_ID, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAnnouncement_throwsNotFound_whenMissing() {
        AnnouncementService service = buildService();
        CommunityMember resident = member(CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));
        when(announcementRepository.findByIdAndCommunity_Id(99L, COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnnouncement(COMMUNITY_ID, 99L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteAnnouncement_deletes_whenCallerIsAdmin() {
        AnnouncementService service = buildService();
        CommunityMember admin = member(CommunityRole.ADMIN);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        Announcement announcement = new Announcement();
        announcement.setId(10L);
        when(announcementRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(announcement));

        service.deleteAnnouncement(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        verify(announcementRepository).delete(announcement);
    }

    @Test
    void deleteAnnouncement_throwsAccessDenied_whenCallerNotAdmin() {
        AnnouncementService service = buildService();
        CommunityMember resident = member(CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> service.deleteAnnouncement(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(announcementRepository, never()).delete(any());
    }
}
