package com.raitukashtam.mycommunity.repository;

import com.raitukashtam.mycommunity.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByCommunity_IdOrderByExpenseDateDescCreatedAtDesc(Long communityId);

    Optional<Expense> findByIdAndCommunity_Id(Long id, Long communityId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.community.id = :communityId")
    BigDecimal sumAmountByCommunity_Id(@Param("communityId") Long communityId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.community.id = :communityId AND e.expenseDate >= :start AND e.expenseDate < :end")
    BigDecimal sumAmountByCommunity_IdAndExpenseDateBetween(
            @Param("communityId") Long communityId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
