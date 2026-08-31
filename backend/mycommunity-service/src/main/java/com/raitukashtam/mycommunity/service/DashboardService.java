package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Announcement;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.entity.Payment;
import com.raitukashtam.mycommunity.entity.BillStatus;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.AnnouncementRepository;
import com.raitukashtam.mycommunity.repository.BillRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.ExpenseRepository;
import com.raitukashtam.mycommunity.repository.PaymentRepository;
import com.raitukashtam.mycommunity.response.ActivityItemResponse;
import com.raitukashtam.mycommunity.response.ActivityType;
import com.raitukashtam.mycommunity.response.AnnouncementResponse;
import com.raitukashtam.mycommunity.response.DashboardResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A derived/union read-model over Community/CommunityMember/Bill/Payment/
 * Expense/Announcement -- no separate activity-log or snapshot table, to
 * avoid duplicating data that already exists elsewhere (same reasoning
 * the original data-model plan used for "Recent Activity"). ADMIN-only,
 * matching every other financial-aggregate endpoint in this service
 * (listBills/listPayments/listExpenses "all" views).
 */
@Service
@Slf4j
public class DashboardService {
    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private AnnouncementService announcementService;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long communityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);

        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community not found with id: " + communityId));

        long occupiedUnits = communityMemberRepository.countByCommunity_IdAndStatus(communityId, MemberStatus.ACTIVE);
        long vacantUnits = Math.max(community.getTotalUnits() - occupiedUnits, 0);

        var pendingDuesTotal = billRepository.sumAmountByCommunity_IdAndStatus(communityId, BillStatus.PENDING);

        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);
        var maintenanceCollectedThisMonth = paymentRepository.sumAmountByCommunity_IdAndPaidAtBetween(
                communityId, thisMonth.atDay(1).atStartOfDay(), thisMonth.plusMonths(1).atDay(1).atStartOfDay());
        var expensesThisMonth = expenseRepository.sumAmountByCommunity_IdAndExpenseDateBetween(
                communityId, thisMonth.atDay(1), thisMonth.plusMonths(1).atDay(1));
        var expensesLastMonth = expenseRepository.sumAmountByCommunity_IdAndExpenseDateBetween(
                communityId, lastMonth.atDay(1), thisMonth.atDay(1));

        var communityBalance = paymentRepository.sumAmountByCommunity_Id(communityId)
                .subtract(expenseRepository.sumAmountByCommunity_Id(communityId));

        List<Payment> recentPayments = paymentRepository.findTop10ByCommunity_IdOrderByPaidAtDesc(communityId);
        List<Announcement> recentAnnouncements = announcementRepository.findTop10ByCommunity_IdOrderByCreatedAtDesc(communityId);

        List<ActivityItemResponse> recentActivity = new ArrayList<>();
        for (Payment payment : recentPayments) {
            recentActivity.add(new ActivityItemResponse(
                    ActivityType.PAYMENT,
                    "Maintenance payment - " + payment.getBill().getPeriod(),
                    payment.getAmount(),
                    payment.getBill().getMember().getName(),
                    payment.getPaidAt()));
        }
        for (Announcement announcement : recentAnnouncements) {
            recentActivity.add(new ActivityItemResponse(
                    ActivityType.ANNOUNCEMENT,
                    announcement.getTitle(),
                    null,
                    announcement.getPostedBy().getName(),
                    announcement.getCreatedAt()));
        }
        recentActivity.sort(Comparator.comparing(ActivityItemResponse::getOccurredAt).reversed());
        List<ActivityItemResponse> topActivity = recentActivity.stream().limit(10).toList();

        List<AnnouncementResponse> topAnnouncements = recentAnnouncements.stream()
                .limit(5)
                .map(announcementService::toResponse)
                .toList();

        return new DashboardResponse(
                community.getId(),
                community.getName(),
                community.getTotalUnits(),
                occupiedUnits,
                vacantUnits,
                pendingDuesTotal,
                maintenanceCollectedThisMonth,
                expensesThisMonth,
                expensesLastMonth,
                communityBalance,
                topAnnouncements,
                topActivity);
    }
}
