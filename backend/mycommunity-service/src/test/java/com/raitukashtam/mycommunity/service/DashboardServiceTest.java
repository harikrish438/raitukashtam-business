package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Announcement;
import com.raitukashtam.mycommunity.entity.Bill;
import com.raitukashtam.mycommunity.entity.BillStatus;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.entity.Payment;
import com.raitukashtam.mycommunity.entity.PaymentMethod;
import com.raitukashtam.mycommunity.repository.AnnouncementRepository;
import com.raitukashtam.mycommunity.repository.BillRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.ExpenseRepository;
import com.raitukashtam.mycommunity.repository.PaymentRepository;
import com.raitukashtam.mycommunity.response.ActivityType;
import com.raitukashtam.mycommunity.response.DashboardResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;
    @Mock
    private BillRepository billRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private AnnouncementRepository announcementRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private DashboardService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        AnnouncementService announcementService = new AnnouncementService();
        setField(announcementService, "announcementRepository", announcementRepository);
        setField(announcementService, "communityRepository", communityRepository);
        setField(announcementService, "communityService", communityService);

        DashboardService service = new DashboardService();
        setField(service, "communityRepository", communityRepository);
        setField(service, "communityMemberRepository", communityMemberRepository);
        setField(service, "billRepository", billRepository);
        setField(service, "paymentRepository", paymentRepository);
        setField(service, "expenseRepository", expenseRepository);
        setField(service, "announcementRepository", announcementRepository);
        setField(service, "communityService", communityService);
        setField(service, "announcementService", announcementService);
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

    private Community community(Long id, int totalUnits) {
        Community community = new Community();
        community.setId(id);
        community.setName("Green Valley Apartments");
        community.setTotalUnits(totalUnits);
        return community;
    }

    private CommunityMember member(Long id, CommunityRole role) {
        CommunityMember member = new CommunityMember();
        member.setId(id);
        member.setName("Member " + id);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        return member;
    }

    private void stubAdmin(CommunityMember admin) {
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
    }

    @Test
    void getDashboard_aggregatesTotals_whenCallerIsAdmin() {
        DashboardService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubAdmin(admin);
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community(COMMUNITY_ID, 20)));
        when(communityMemberRepository.countByCommunity_IdAndStatus(COMMUNITY_ID, MemberStatus.ACTIVE)).thenReturn(12L);
        when(billRepository.sumAmountByCommunity_IdAndStatus(COMMUNITY_ID, BillStatus.PENDING)).thenReturn(new BigDecimal("4500.00"));
        when(paymentRepository.sumAmountByCommunity_IdAndPaidAtBetween(eq(COMMUNITY_ID), any(), any())).thenReturn(new BigDecimal("15000.00"));
        when(expenseRepository.sumAmountByCommunity_IdAndExpenseDateBetween(eq(COMMUNITY_ID), any(), any()))
                .thenReturn(new BigDecimal("3000.00"), new BigDecimal("2000.00"));
        when(paymentRepository.sumAmountByCommunity_Id(COMMUNITY_ID)).thenReturn(new BigDecimal("50000.00"));
        when(expenseRepository.sumAmountByCommunity_Id(COMMUNITY_ID)).thenReturn(new BigDecimal("20000.00"));
        when(paymentRepository.findTop10ByCommunity_IdOrderByPaidAtDesc(COMMUNITY_ID)).thenReturn(List.of());
        when(announcementRepository.findTop10ByCommunity_IdOrderByCreatedAtDesc(COMMUNITY_ID)).thenReturn(List.of());

        DashboardResponse response = service.getDashboard(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(response.getCommunityName()).isEqualTo("Green Valley Apartments");
        assertThat(response.getTotalUnits()).isEqualTo(20);
        assertThat(response.getOccupiedUnits()).isEqualTo(12L);
        assertThat(response.getVacantUnits()).isEqualTo(8L);
        assertThat(response.getPendingDuesTotal()).isEqualByComparingTo("4500.00");
        assertThat(response.getMaintenanceCollectedThisMonth()).isEqualByComparingTo("15000.00");
        assertThat(response.getExpensesThisMonth()).isEqualByComparingTo("3000.00");
        assertThat(response.getExpensesLastMonth()).isEqualByComparingTo("2000.00");
        assertThat(response.getCommunityBalance()).isEqualByComparingTo("30000.00");
    }

    @Test
    void getDashboard_vacantUnitsFloorsAtZero_whenOccupiedExceedsTotal() {
        DashboardService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubAdmin(admin);
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community(COMMUNITY_ID, 5)));
        when(communityMemberRepository.countByCommunity_IdAndStatus(COMMUNITY_ID, MemberStatus.ACTIVE)).thenReturn(7L);
        when(billRepository.sumAmountByCommunity_IdAndStatus(COMMUNITY_ID, BillStatus.PENDING)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumAmountByCommunity_IdAndPaidAtBetween(eq(COMMUNITY_ID), any(), any())).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumAmountByCommunity_IdAndExpenseDateBetween(eq(COMMUNITY_ID), any(), any())).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumAmountByCommunity_Id(COMMUNITY_ID)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumAmountByCommunity_Id(COMMUNITY_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.findTop10ByCommunity_IdOrderByPaidAtDesc(COMMUNITY_ID)).thenReturn(List.of());
        when(announcementRepository.findTop10ByCommunity_IdOrderByCreatedAtDesc(COMMUNITY_ID)).thenReturn(List.of());

        DashboardResponse response = service.getDashboard(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(response.getVacantUnits()).isEqualTo(0L);
    }

    @Test
    void getDashboard_mergesAndSortsRecentActivity_newestFirst() {
        DashboardService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        stubAdmin(admin);
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community(COMMUNITY_ID, 20)));
        when(communityMemberRepository.countByCommunity_IdAndStatus(COMMUNITY_ID, MemberStatus.ACTIVE)).thenReturn(1L);
        when(billRepository.sumAmountByCommunity_IdAndStatus(COMMUNITY_ID, BillStatus.PENDING)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumAmountByCommunity_IdAndPaidAtBetween(eq(COMMUNITY_ID), any(), any())).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumAmountByCommunity_IdAndExpenseDateBetween(eq(COMMUNITY_ID), any(), any())).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumAmountByCommunity_Id(COMMUNITY_ID)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumAmountByCommunity_Id(COMMUNITY_ID)).thenReturn(BigDecimal.ZERO);

        CommunityMember payer = member(6L, CommunityRole.RESIDENT);
        Bill bill = new Bill();
        bill.setPeriod("2026-09");
        bill.setMember(payer);
        Payment olderPayment = new Payment();
        olderPayment.setBill(bill);
        olderPayment.setAmount(new BigDecimal("1500.00"));
        olderPayment.setMethod(PaymentMethod.CASH);
        olderPayment.setPaidAt(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(paymentRepository.findTop10ByCommunity_IdOrderByPaidAtDesc(COMMUNITY_ID)).thenReturn(List.of(olderPayment));

        Announcement newerAnnouncement = new Announcement();
        newerAnnouncement.setTitle("Water shutdown");
        newerAnnouncement.setBody("body");
        newerAnnouncement.setPostedBy(admin);
        newerAnnouncement.setCommunity(community(COMMUNITY_ID, 20));
        newerAnnouncement.setCreatedAt(LocalDateTime.of(2026, 8, 25, 9, 0));
        when(announcementRepository.findTop10ByCommunity_IdOrderByCreatedAtDesc(COMMUNITY_ID)).thenReturn(List.of(newerAnnouncement));

        DashboardResponse response = service.getDashboard(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(response.getRecentActivity()).hasSize(2);
        assertThat(response.getRecentActivity().get(0).getType()).isEqualTo(ActivityType.ANNOUNCEMENT);
        assertThat(response.getRecentActivity().get(0).getTitle()).isEqualTo("Water shutdown");
        assertThat(response.getRecentActivity().get(1).getType()).isEqualTo(ActivityType.PAYMENT);
        assertThat(response.getRecentActivity().get(1).getActorName()).isEqualTo("Member 6");
        assertThat(response.getRecentAnnouncements()).hasSize(1);
        assertThat(response.getRecentAnnouncements().get(0).getTitle()).isEqualTo("Water shutdown");
    }

    @Test
    void getDashboard_throwsAccessDenied_whenCallerNotAdmin() {
        DashboardService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> service.getDashboard(COMMUNITY_ID, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }
}
