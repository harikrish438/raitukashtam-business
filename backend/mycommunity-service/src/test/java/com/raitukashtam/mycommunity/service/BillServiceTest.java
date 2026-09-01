package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Bill;
import com.raitukashtam.mycommunity.entity.BillStatus;
import com.raitukashtam.mycommunity.entity.BillingMode;
import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.entity.Unit;
import com.raitukashtam.mycommunity.exception.ResourceAlreadyExistsException;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.BillRepository;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.request.GenerateBillsRequest;
import com.raitukashtam.mycommunity.response.BillResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillServiceTest {

    @Mock
    private BillRepository billRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private BillService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        BillService service = new BillService();
        setField(service, "billRepository", billRepository);
        setField(service, "communityRepository", communityRepository);
        setField(service, "communityMemberRepository", communityMemberRepository);
        setField(service, "communityService", communityService);
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

    private GenerateBillsRequest generateRequest() {
        GenerateBillsRequest request = new GenerateBillsRequest();
        request.setPeriod("2026-09");
        request.setAmount(new BigDecimal("1500.00"));
        request.setDueDate(LocalDate.of(2026, 9, 10));
        return request;
    }

    @Test
    void generateBills_createsOneBillPerActiveMember_whenCallerIsAdmin() {
        BillService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(billRepository.existsByCommunity_IdAndPeriod(COMMUNITY_ID, "2026-09")).thenReturn(false);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityMemberRepository.findByCommunity_IdAndStatus(COMMUNITY_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of(admin, resident));
        when(billRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Bill> bills = invocation.getArgument(0);
            long id = 100L;
            for (Bill bill : bills) {
                bill.setId(id++);
            }
            return bills;
        });

        List<BillResponse> responses = service.generateBills(COMMUNITY_ID, generateRequest(), CALLER_IDENTITY);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(BillResponse::getMemberId).containsExactlyInAnyOrder(5L, 6L);
        assertThat(responses).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(BillStatus.PENDING);
            assertThat(r.getAmount()).isEqualByComparingTo("1500.00");
            assertThat(r.getPeriod()).isEqualTo("2026-09");
        });
    }

    @Test
    void generateBills_throwsAlreadyExists_whenPeriodAlreadyGenerated() {
        BillService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(billRepository.existsByCommunity_IdAndPeriod(COMMUNITY_ID, "2026-09")).thenReturn(true);

        assertThatThrownBy(() -> service.generateBills(COMMUNITY_ID, generateRequest(), CALLER_IDENTITY))
                .isInstanceOf(ResourceAlreadyExistsException.class);
        verify(billRepository, never()).saveAll(anyList());
    }

    @Test
    void generateBills_throwsAccessDenied_whenCallerNotAdmin() {
        BillService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> service.generateBills(COMMUNITY_ID, generateRequest(), CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listMyBills_returnsOnlyCallersBills() {
        BillService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        Bill bill = new Bill();
        bill.setId(100L);
        bill.setCommunity(community(COMMUNITY_ID));
        bill.setMember(resident);
        bill.setPeriod("2026-09");
        bill.setAmount(new BigDecimal("1500.00"));
        bill.setStatus(BillStatus.PENDING);
        bill.setDueDate(LocalDate.of(2026, 9, 10));
        when(billRepository.findByMember_IdOrderByPeriodDescCreatedAtDesc(6L)).thenReturn(List.of(bill));

        List<BillResponse> result = service.listMyBills(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMemberId()).isEqualTo(6L);
    }

    @Test
    void getBill_throwsAccessDenied_whenCallerIsNeitherOwnerNorAdmin() {
        BillService service = buildService();
        CommunityMember otherResident = member(7L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(otherResident));

        Bill bill = new Bill();
        bill.setId(100L);
        bill.setCommunity(community(COMMUNITY_ID));
        bill.setMember(member(6L, CommunityRole.RESIDENT));
        when(billRepository.findByIdAndCommunity_Id(100L, COMMUNITY_ID)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service.getBill(COMMUNITY_ID, 100L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getBill_throwsNotFound_whenMissing() {
        BillService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));
        when(billRepository.findByIdAndCommunity_Id(999L, COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBill(COMMUNITY_ID, 999L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Community perAreaCommunity(BigDecimal ratePerSqft) {
        Community community = community(COMMUNITY_ID);
        community.setBillingMode(BillingMode.PER_AREA);
        community.setRatePerSqft(ratePerSqft);
        return community;
    }

    private CommunityMember memberWithUnit(Long id, CommunityRole role, BigDecimal areaSqft) {
        CommunityMember member = member(id, role);
        Unit unit = new Unit();
        unit.setId(id + 900);
        unit.setUnitNumber(member.getUnitNumber());
        unit.setAreaSqft(areaSqft);
        member.setUnit(unit);
        return member;
    }

    @Test
    void generateBills_computesPerMemberAmount_whenBillingModeIsPerArea() {
        BillService service = buildService();
        CommunityMember admin = memberWithUnit(5L, CommunityRole.ADMIN, new BigDecimal("1000.00"));
        CommunityMember resident = memberWithUnit(6L, CommunityRole.RESIDENT, new BigDecimal("1500.00"));
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(billRepository.existsByCommunity_IdAndPeriod(COMMUNITY_ID, "2026-09")).thenReturn(false);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(perAreaCommunity(new BigDecimal("2.50")));
        when(communityMemberRepository.findByCommunity_IdAndStatus(COMMUNITY_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of(admin, resident));
        when(billRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Bill> bills = invocation.getArgument(0);
            long id = 200L;
            for (Bill bill : bills) {
                bill.setId(id++);
            }
            return bills;
        });

        GenerateBillsRequest request = new GenerateBillsRequest();
        request.setPeriod("2026-09");
        request.setDueDate(LocalDate.of(2026, 9, 10));

        List<BillResponse> responses = service.generateBills(COMMUNITY_ID, request, CALLER_IDENTITY);

        assertThat(responses).hasSize(2);
        assertThat(responses).filteredOn(r -> r.getMemberId().equals(5L)).first()
                .satisfies(r -> assertThat(r.getAmount()).isEqualByComparingTo("2500.00"));
        assertThat(responses).filteredOn(r -> r.getMemberId().equals(6L)).first()
                .satisfies(r -> assertThat(r.getAmount()).isEqualByComparingTo("3750.00"));
    }

    @Test
    void generateBills_throwsBadRequest_whenPerAreaAndAmountProvided() {
        BillService service = buildService();
        CommunityMember admin = memberWithUnit(5L, CommunityRole.ADMIN, new BigDecimal("1000.00"));
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(billRepository.existsByCommunity_IdAndPeriod(COMMUNITY_ID, "2026-09")).thenReturn(false);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(perAreaCommunity(new BigDecimal("2.50")));
        when(communityMemberRepository.findByCommunity_IdAndStatus(COMMUNITY_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.generateBills(COMMUNITY_ID, generateRequest(), CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must not be provided");
        verify(billRepository, never()).saveAll(anyList());
    }

    @Test
    void generateBills_throwsConflict_whenPerAreaAndMemberHasNoUnitArea() {
        BillService service = buildService();
        CommunityMember admin = memberWithUnit(5L, CommunityRole.ADMIN, new BigDecimal("1000.00"));
        CommunityMember residentWithoutUnit = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(billRepository.existsByCommunity_IdAndPeriod(COMMUNITY_ID, "2026-09")).thenReturn(false);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(perAreaCommunity(new BigDecimal("2.50")));
        when(communityMemberRepository.findByCommunity_IdAndStatus(COMMUNITY_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of(admin, residentWithoutUnit));

        GenerateBillsRequest request = new GenerateBillsRequest();
        request.setPeriod("2026-09");
        request.setDueDate(LocalDate.of(2026, 9, 10));

        assertThatThrownBy(() -> service.generateBills(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no unit area assigned");
        verify(billRepository, never()).saveAll(anyList());
    }

    @Test
    void generateBills_throwsBadRequest_whenFlatAndAmountMissing() {
        BillService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(billRepository.existsByCommunity_IdAndPeriod(COMMUNITY_ID, "2026-09")).thenReturn(false);
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(communityMemberRepository.findByCommunity_IdAndStatus(COMMUNITY_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of(admin));

        GenerateBillsRequest request = new GenerateBillsRequest();
        request.setPeriod("2026-09");
        request.setDueDate(LocalDate.of(2026, 9, 10));

        assertThatThrownBy(() -> service.generateBills(COMMUNITY_ID, request, CALLER_IDENTITY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Amount is required");
        verify(billRepository, never()).saveAll(anyList());
    }
}
