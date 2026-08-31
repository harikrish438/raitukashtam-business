package com.raitukashtam.mycommunity.service;

import com.raitukashtam.mycommunity.entity.Community;
import com.raitukashtam.mycommunity.entity.CommunityMember;
import com.raitukashtam.mycommunity.entity.Expense;
import com.raitukashtam.mycommunity.exception.ResourceNotFoundException;
import com.raitukashtam.mycommunity.repository.CommunityRepository;
import com.raitukashtam.mycommunity.repository.ExpenseRepository;
import com.raitukashtam.mycommunity.request.ExpenseRequest;
import com.raitukashtam.mycommunity.response.ExpenseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * ADMIN-only end to end, per the user's spec -- unlike Bills/Payments,
 * expenses have no natural "my expenses" subset (they belong to the
 * community as a whole, not an individual member), so there's no
 * resident-facing read path here at all. Membership authorization is
 * delegated to CommunityService, same pattern as every other phase.
 */
@Service
@Slf4j
public class ExpenseService {
    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityService communityService;

    @Transactional
    public ExpenseResponse createExpense(Long communityId, ExpenseRequest request, String callerIdentityId) {
        CommunityMember admin = communityService.requireActiveAdmin(communityId, callerIdentityId);

        Community community = communityRepository.getReferenceById(communityId);
        Expense expense = new Expense();
        expense.setCommunity(community);
        expense.setCategory(request.getCategory().trim());
        expense.setDescription(request.getDescription().trim());
        expense.setAmount(request.getAmount());
        expense.setExpenseDate(request.getExpenseDate() != null ? request.getExpenseDate() : LocalDate.now());
        expense.setCreatedByMember(admin);
        Expense saved = expenseRepository.save(expense);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listExpenses(Long communityId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return expenseRepository.findByCommunity_IdOrderByExpenseDateDescCreatedAtDesc(communityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(Long communityId, Long expenseId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        return toResponse(requireExpense(communityId, expenseId));
    }

    @Transactional
    public void deleteExpense(Long communityId, Long expenseId, String callerIdentityId) {
        communityService.requireActiveAdmin(communityId, callerIdentityId);
        expenseRepository.delete(requireExpense(communityId, expenseId));
    }

    private Expense requireExpense(Long communityId, Long expenseId) {
        return expenseRepository.findByIdAndCommunity_Id(expenseId, communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + expenseId));
    }

    private ExpenseResponse toResponse(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getCommunity().getId(),
                expense.getCategory(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getExpenseDate(),
                expense.getCreatedByMember().getId(),
                expense.getCreatedByMember().getName(),
                expense.getCreatedAt());
    }
}
