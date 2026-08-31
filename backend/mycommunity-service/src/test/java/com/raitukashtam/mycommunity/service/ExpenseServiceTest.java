package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.Expense;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityMemberRepository;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.ExpenseRepository;
import com.raitukashtam.mycommunity.request.ExpenseRequest;
import com.raitukashtam.mycommunity.response.ExpenseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityMemberRepository communityMemberRepository;

    private static final Long COMMUNITY_ID = 1L;
    private static final String CALLER_IDENTITY = "22222222-2222-2222-2222-222222222222";

    private ExpenseService buildService() {
        CommunityService communityService = new CommunityService();
        setField(communityService, "communityRepository", communityRepository);
        setField(communityService, "communityMemberRepository", communityMemberRepository);

        ExpenseService service = new ExpenseService();
        setField(service, "expenseRepository", expenseRepository);
        setField(service, "communityRepository", communityRepository);
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
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        member.setCommunity(community(COMMUNITY_ID));
        return member;
    }

    private ExpenseRequest expenseRequest() {
        ExpenseRequest request = new ExpenseRequest();
        request.setCategory("Maintenance");
        request.setDescription("Generator AMC visit");
        request.setAmount(new BigDecimal("2500.00"));
        return request;
    }

    @Test
    void createExpense_savesExpense_whenCallerIsActiveAdmin() {
        ExpenseService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense e = invocation.getArgument(0);
            e.setId(10L);
            return e;
        });

        ExpenseResponse response = service.createExpense(COMMUNITY_ID, expenseRequest(), CALLER_IDENTITY);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCategory()).isEqualTo("Maintenance");
        assertThat(response.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(response.getExpenseDate()).isEqualTo(LocalDate.now());
        assertThat(response.getCreatedByMemberId()).isEqualTo(5L);
    }

    @Test
    void createExpense_usesProvidedExpenseDate_whenBackDated() {
        ExpenseService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(communityRepository.getReferenceById(COMMUNITY_ID)).thenReturn(community(COMMUNITY_ID));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseRequest request = expenseRequest();
        LocalDate backDated = LocalDate.now().minusDays(3);
        request.setExpenseDate(backDated);

        ExpenseResponse response = service.createExpense(COMMUNITY_ID, request, CALLER_IDENTITY);

        assertThat(response.getExpenseDate()).isEqualTo(backDated);
    }

    @Test
    void createExpense_throwsAccessDenied_whenCallerNotAdmin() {
        ExpenseService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> service.createExpense(COMMUNITY_ID, expenseRequest(), CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void listExpenses_throwsAccessDenied_whenCallerNotAdmin() {
        ExpenseService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> service.listExpenses(COMMUNITY_ID, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listExpenses_returnsOrderedList_whenCallerIsAdmin() {
        ExpenseService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));

        Expense expense = new Expense();
        expense.setId(10L);
        expense.setCommunity(community(COMMUNITY_ID));
        expense.setCategory("Maintenance");
        expense.setDescription("Generator AMC visit");
        expense.setAmount(new BigDecimal("2500.00"));
        expense.setExpenseDate(LocalDate.now());
        expense.setCreatedByMember(admin);
        when(expenseRepository.findByCommunity_IdOrderByExpenseDateDescCreatedAtDesc(COMMUNITY_ID))
                .thenReturn(List.of(expense));

        List<ExpenseResponse> result = service.listExpenses(COMMUNITY_ID, CALLER_IDENTITY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("Maintenance");
    }

    @Test
    void getExpense_throwsNotFound_whenMissing() {
        ExpenseService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        when(expenseRepository.findByIdAndCommunity_Id(99L, COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExpense(COMMUNITY_ID, 99L, CALLER_IDENTITY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteExpense_deletes_whenCallerIsAdmin() {
        ExpenseService service = buildService();
        CommunityMember admin = member(5L, CommunityRole.ADMIN);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(admin));
        Expense expense = new Expense();
        expense.setId(10L);
        when(expenseRepository.findByIdAndCommunity_Id(10L, COMMUNITY_ID)).thenReturn(Optional.of(expense));

        service.deleteExpense(COMMUNITY_ID, 10L, CALLER_IDENTITY);

        verify(expenseRepository).delete(expense);
    }

    @Test
    void deleteExpense_throwsAccessDenied_whenCallerNotAdmin() {
        ExpenseService service = buildService();
        CommunityMember resident = member(6L, CommunityRole.RESIDENT);
        when(communityRepository.existsById(COMMUNITY_ID)).thenReturn(true);
        when(communityMemberRepository.findByCommunity_IdAndIdentityIdAndStatus(COMMUNITY_ID, CALLER_IDENTITY, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(resident));

        assertThatThrownBy(() -> service.deleteExpense(COMMUNITY_ID, 10L, CALLER_IDENTITY))
                .isInstanceOf(AccessDeniedException.class);
        verify(expenseRepository, never()).delete(any());
    }
}
