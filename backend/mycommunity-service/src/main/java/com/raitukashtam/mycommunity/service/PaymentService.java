package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Bill;
import com.raitukashtam.mycommunity.entity.BillStatus;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.Payment;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.BillRepository;
import com.raitukashtam.mycommunity.repository.PaymentRepository;
import com.raitukashtam.mycommunity.request.RecordPaymentRequest;
import com.raitukashtam.mycommunity.response.PaymentResponse;
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
 * Records a single full payment against a Bill (no partial payments in
 * v1) and flips that Bill to PAID -- the richer, receipt-carrying
 * replacement for Phase 3's bare BillService.markPaid, which this phase
 * removes. Membership authorization is delegated to CommunityService;
 * bill lookup is delegated to BillService.requireBill (package-private,
 * same package) rather than duplicating it here.
 */
@Service
@Slf4j
public class PaymentService {
    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private BillService billService;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public PaymentResponse recordPayment(Long communityId, Long billId, RecordPaymentRequest request, String callerIdentityId) {
        CommunityMember admin = communityService.requireActiveAdmin(communityId, callerIdentityId);
        Bill bill = billService.requireBill(communityId, billId);

        if (bill.getStatus() == BillStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bill is already marked paid");
        }

        LocalDateTime paidAt = request.getPaidAt() != null ? request.getPaidAt() : LocalDateTime.now();

        Payment payment = new Payment();
        payment.setCommunity(bill.getCommunity());
        payment.setBill(bill);
        payment.setAmount(bill.getAmount());
        payment.setMethod(request.getMethod());
        payment.setReference(request.getReference());
        payment.setPaidAt(paidAt);
        payment.setRecordedBy(admin);
        Payment savedPayment = paymentRepository.save(payment);

        bill.setStatus(BillStatus.PAID);
        bill.setPaidAt(paidAt);
        billRepository.save(bill);

        notificationService.notifyIdentity(bill.getMember().getIdentityId(),
                "Payment received", "Your payment for " + bill.getPeriod() + " (₹" + bill.getAmount() + ") has been recorded.");

        return toResponse(savedPayment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForBill(Long communityId, Long billId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        Bill bill = billService.requireBill(communityId, billId);
        if (!bill.getMember().getId().equals(caller.getId()) && caller.getRole() != CommunityRole.ADMIN) {
            throw new AccessDeniedException("Not authorized to view this bill's payment");
        }

        Payment payment = paymentRepository.findByBill_Id(billId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment recorded for bill with id: " + billId));
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(Long communityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return paymentRepository.findByCommunity_IdOrderByPaidAtDesc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listMyPayments(Long communityId, String callerIdentityId) {
        CommunityMember caller = communityService.requireActiveMember(communityId, callerIdentityId);
        return paymentRepository.findByBill_Member_IdOrderByPaidAtDesc(caller.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private PaymentResponse toResponse(Payment payment) {
        Bill bill = payment.getBill();
        return new PaymentResponse(
                payment.getId(),
                payment.getCommunity().getId(),
                bill.getId(),
                bill.getPeriod(),
                bill.getMember().getId(),
                bill.getMember().getName(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getReference(),
                payment.getPaidAt(),
                payment.getRecordedBy().getId(),
                payment.getRecordedBy().getName());
    }
}
