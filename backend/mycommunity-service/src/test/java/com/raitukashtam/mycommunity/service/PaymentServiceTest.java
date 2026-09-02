package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Bill;
import com.raitukashtam.mycommunity.entity.BillStatus;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.entity.Payment;
import com.raitukashtam.mycommunity.entity.PaymentMethod;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.BillRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.PaymentRepository;
import com.raitukashtam.mycommunity.request.RecordPaymentRequest;
import com.raitukashtam.mycommunity.response.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BillRepository billRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;
    @Mock
    private NotificationService notificationService;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private PaymentService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        BillService billService = new BillService();
        setField(billService, "billRepository", billRepository);
        setField(billService, "communityRepository", communityRepository);
        setField(billService, "communityMemberRepository", communityMemberRepository);
        setField(billService, "communityService", communityService);

        PaymentService service = new PaymentService();
        setField(service, "paymentRepository", paymentRepository);
        setField(service, "billRepository", billRepository);
        setField(service, "billService", billService);
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

    private CommunityMember member(Long id, CommunityRole role) {
        CommunityMember member = new CommunityMember();
        member.setId(id);
        member.setName("Member " + id);
        member.setUnitNumber("A-" + id);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        member.setCommunity(community(COMMUNITY_ID));
        return member;
    }

    private Bill bill(Long id, CommunityMember owner, BillStatus status) {
        Bill bill = new Bill();
        bill.setId(id);
        bill.setCommunity(community(COMMUNITY_ID));
        bill.setMember(owner);
        bill.setPeriod("2026-09");
        bill.setAmount(new BigDecimal("1500.00"));
        bill.setStatus(status);
        bill.setDueDate(LocalDate.of(2026, 9, 10));
        return bill;
    }

    private RecordPaymentRequest paymentRequest() {
        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setMethod(PaymentMethod.UPI);
        request.setReference("UPI-REF-123");
        return request;
    }

    @Test
    void recordPayment_createsPaymentAndMarksBillPaid_whenCallerIsAdmin() {
        PaymentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        Bill bill = bill(100L, resident, BillStatus.PENDING);
        when(billRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(bill));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(500L);
            return p;
        });

        PaymentResponse response = service.recordPayment(COMMUNITY_ID, 100L, paymentRequest(), CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getAmount()).isEqualByComparingTo("1500.00");
        assertThat(response.getMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(response.getReference()).isEqualTo("UPI-REF-123");
        assertThat(response.getRecordedByMemberId()).isEqualTo(5L);
        assertThat(bill.getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(bill.getPaidAt()).isNotNull();
        verify(billRepository).save(bill);
    }

    @Test
    void recordPayment_usesProvidedPaidAt_whenBackDated() {
        PaymentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        Bill bill = bill(100L, resident, BillStatus.PENDING);
        when(billRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(bill));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordPaymentRequest request = paymentRequest();
        LocalDateTime backDated = LocalDateTime.of(2026, 9, 5, 10, 0);
        request.setPaidAt(backDated);

        PaymentResponse response = service.recordPayment(COMMUNITY_ID, 100L, request, CALLER_IDENTITY);

        assertThat(response.getPaidAt()).isEqualTo(backDated);
        assertThat(bill.getPaidAt()).isEqualTo(backDated);
    }

    @Test
    void recordPayment_throwsConflict_whenBillAlreadyPaid() {
        PaymentService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        Bill bill = bill(100L, resident, BillStatus.PAID);
        when(billRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service.recordPayment(COMMUNITY_ID, 100L, paymentRequest(), CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void recordPayment_throwsAccessDenied_whenCallerNotAdmin() {
        PaymentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> service.recordPayment(COMMUNITY_ID, 100L, paymentRequest(), CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void getPaymentForBill_returnsPayment_whenCallerIsBillOwner() {
        PaymentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));
        Bill bill = bill(100L, resident, BillStatus.PAID);
        when(billRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(bill));

        Payment payment = new Payment();
        payment.setId(500L);
        payment.setCommunity(community(COMMUNITY_ID));
        payment.setBill(bill);
        payment.setAmount(new BigDecimal("1500.00"));
        payment.setMethod(PaymentMethod.CASH);
        payment.setPaidAt(LocalDateTime.now());
        payment.setRecordedBy(member(5L, CommunityRole.ADMIN));
        when(paymentRepository.findByBill_Id(100L)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.getPaymentForBill(COMMUNITY_ID, 100L, CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void getPaymentForBill_throwsAccessDenied_whenCallerIsNeitherOwnerNorAdmin() {
        PaymentService service = buildService();
        CommunityMember otherResident = member(7L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(otherResident));
        Bill bill = bill(100L, member(6L, CommunityRole.RESIDENT), BillStatus.PAID);
        when(billRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service.getPaymentForBill(COMMUNITY_ID, 100L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getPaymentForBill_throwsNotFound_whenBillNotYetPaid() {
        PaymentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));
        Bill bill = bill(100L, resident, BillStatus.PENDING);
        when(billRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(bill));
        when(paymentRepository.findByBill_Id(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPaymentForBill(COMMUNITY_ID, 100L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listMyPayments_returnsOnlyCallersPayments() {
        PaymentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        Bill bill = bill(100L, resident, BillStatus.PAID);
        Payment payment = new Payment();
        payment.setId(500L);
        payment.setCommunity(community(COMMUNITY_ID));
        payment.setBill(bill);
        payment.setAmount(new BigDecimal("1500.00"));
        payment.setMethod(PaymentMethod.CASH);
        payment.setPaidAt(LocalDateTime.now());
        payment.setRecordedBy(member(5L, CommunityRole.ADMIN));
        when(paymentRepository.findByBill_Member_IdOrderByPaidAtDesc(6L)).thenReturn(List.of(payment));

        List<PaymentResponse> result = service.listMyPayments(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMemberId()).isEqualTo(6L);
    }

    @Test
    void listPayments_throwsAccessDenied_whenCallerNotAdmin() {
        PaymentService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> service.listPayments(COMMUNITY_ID, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }
}
