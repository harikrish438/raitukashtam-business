package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface TokenBlackListRepository extends JpaRepository<RevokedToken, String> {
    
    Optional<RevokedToken> findByJti(String jti);
    
    boolean existsByJti(String jti);
    
    @Modifying
    @Query("DELETE FROM RevokedToken rt WHERE rt.expiresAt < :now")
    void deleteAllExpiredSince(@Param("now") Instant now);
}

