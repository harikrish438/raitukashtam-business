package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Bill;
import com.raitukashtam.mycommunity.entity.BillStatus;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * Membership authorization (requireActiveMember/requireActiveAdmin) is
 * delegated to CommunityService, the single source of truth for "who can
 * act in this community" -- same pattern AnnouncementService/
 * CommunityJoinRequestService use. One Bill per ACTIVE member per period,
 * a single flat amount per generation batch -- Community has no per-unit
 * size/area field yet to base a varying amount on.
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

        List<Bill> bills = activeMembers.stream().map(member -> {
            Bill bill = new Bill();
            bill.setCommunity(community);
            bill.setMember(member);
            bill.setPeriod(request.getPeriod());
            bill.setAmount(request.getAmount());
            bill.setStatus(BillStatus.PENDING);
            bill.setDueDate(request.getDueDate());
            return bill;
        }).toList();

        return billRepository.saveAll(bills).stream().map(this::toResponse).toList();
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

    @Transactional
    public BillResponse markPaid(Long communityId, Long billId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        Bill bill = requireBill(communityId, billId);
        if (bill.getStatus() == BillStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill is already marked paid");
        }
        bill.setStatus(BillStatus.PAID);
        bill.setPaidAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    private Bill requireBill(Long communityId, Long billId) {
        return billRepository.findByIdAndCommunity_Id(billId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));
    }

    private BillResponse toResponse(Bill bill) {
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
