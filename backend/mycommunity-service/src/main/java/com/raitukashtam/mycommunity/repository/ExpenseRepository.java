package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByCommunity_IdOrderByExpenseDateDescCreatedAtDesc(Long communityId);

    Optional<Expense> findByIdAndCommunity_Id(Long id, Long communityId);
}
