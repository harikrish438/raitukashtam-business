package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Bill;
import com.raitukashtam.mycommunity.entity.BillStatus;
import com.raitukashtam.mycommunity.entity.BillingMode;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.BillRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.GenerateBillsRequest;
import com.raitukashtam.mycommunity.response.BillResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Membership authorization (requireActiveMember/requireActiveAdmin) is
 * delegated to CommunityService, the single source of truth for "who can
 * act in this community" -- same pattern AnnouncementService/
 * CommunityJoinRequestService use. One Bill per ACTIVE member per period.
 * Amount depends on Community.billingMode (Phase 12): FLAT (default --
 * practical for most communities) applies the request's flat amount to
 * every member unchanged from earlier phases; PER_AREA instead computes
 * each member's amount from their linked Unit's areaSqft x the community's
 * ratePerSqft, and rejects the batch if any ACTIVE member has no Unit/area
 * assigned yet rather than silently under-billing them.
 */
@Service
@Slf4j
public class BillService {
    @Autowired
    private BillRepository billRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityMemberRepository communityMemberRepository;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public List<BillResponse> generateBills(Long communityId, GenerateBillsRequest request, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);

        if (billRepository.existsByCommunity_IdAndPeriod(communityId, request.getPeriod())) {
            throw new ResourceAlreadyExistsException(
                    "Bills for period '" + request.getPeriod() + "' have already been generated for this community");
        }

        Community community = communityRepository.getReferenceById(communityId);
        List<CommunityMember> activeMembers = communityMemberRepository.findByCommunity_IdAndStatus(communityId, MemberStatus.ACTIVE);
        if (activeMembers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No active members to bill in this community");
        }

        List<Bill> bills = community.getBillingMode() == BillingMode.PER_AREA
                ? buildAreaBasedBills(community, activeMembers, request)
                : buildFlatBills(community, activeMembers, request);

        return billRepository.saveAll(bills).stream().map(this::toResponse).toList();
    }

    private List<Bill> buildFlatBills(Community community, List<CommunityMember> activeMembers, GenerateBillsRequest request) {
        if (request.getAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required when billing mode is FLAT");
        }
        return activeMembers.stream().map(member -> newBill(community, member, request, request.getAmount())).toList();
    }

    private List<Bill> buildAreaBasedBills(Community community, List<CommunityMember> activeMembers, GenerateBillsRequest request) {
        if (request.getAmount() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Amount must not be provided when billing mode is PER_AREA -- it's computed per member from the community's rate per sqft");
        }
        BigDecimal ratePerSqft = community.getRatePerSqft();
        if (ratePerSqft == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Billing mode is PER_AREA but no rate per sqft is set for this community");
        }

        List<String> missingArea = activeMembers.stream()
                .filter(member -> member.getUnit() == null || member.getUnit().getAreaSqft() == null)
                .map(member -> member.getName() + " (" + member.getUnitNumber() + ")")
                .toList();
        if (!missingArea.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot generate area-based bills -- these members have no unit area assigned: " + String.join(", ", missingArea));
        }

        return activeMembers.stream().map(member -> {
            BigDecimal amount = ratePerSqft.multiply(member.getUnit().getAreaSqft()).setScale(2, RoundingMode.HALF_UP);
            return newBill(community, member, request, amount);
        }).toList();
    }

    private Bill newBill(Community community, CommunityMember member, GenerateBillsRequest request, BigDecimal amount) {
        Bill bill = new Bill();
        bill.setCommunity(community);
        bill.setMember(member);
        bill.setPeriod(request.getPeriod());
        bill.setAmount(amount);
        bill.setStatus(BillStatus.PENDING);
        bill.setDueDate(request.getDueDate());
        return bill;
    }

    @Transactional(readOnly = true)
    public List<BillResponse> listBills(Long communityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return billRepository.findByCommunity_IdOrderByPeriodDescCreatedAtDesc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BillResponse> listMyBills(Long communityId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        return billRepository.findByMember_IdOrderByPeriodDescCreatedAtDesc(caller.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BillResponse getBill(Long communityId, Long billId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        Bill bill = requireBill(communityId, billId);
        if (!bill.getMember().getId().equals(caller.getId()) && caller.getRole() != CommunityRole.ADMIN) {
            throw new AccessDeniedException("Not authorized to view this bill");
        }
        return toResponse(bill);
    }

    /**
     * Package-private -- reused by PaymentService so recording a payment
     * doesn't duplicate the "bill exists in this community" lookup.
     */
    Bill requireBill(Long communityId, Long billId) {
        return billRepository.findByIdAndCommunity_Id(billId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));
    }

    BillResponse toResponse(Bill bill) {
        return new BillResponse(
                bill.getId(),
                bill.getCommunity().getId(),
                bill.getMember().getId(),
                bill.getMember().getName(),
                bill.getMember().getUnitNumber(),
                bill.getPeriod(),
                bill.getAmount(),
                bill.getStatus(),
                bill.getDueDate(),
                bill.getPaidAt());
    }
}
