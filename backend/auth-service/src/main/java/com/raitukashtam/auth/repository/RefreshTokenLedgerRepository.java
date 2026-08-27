package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.RefreshTokenLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenLedgerRepository extends JpaRepository<RefreshTokenLedgerEntry, Long> {
    Optional<RefreshTokenLedgerEntry> findByTokenHash(String tokenHash);
    boolean existsByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM RefreshTokenLedgerEntry e WHERE e.createdAt < :before")
    void deleteAllCreatedBefore(@Param("before") Instant before);
}
