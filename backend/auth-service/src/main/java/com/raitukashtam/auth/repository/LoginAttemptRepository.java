package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.LoginAttempt;
import com.raitukashtam.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
    
    @Query("SELECT COUNT(la) > 0 FROM LoginAttempt la " +
           "WHERE la.user = :user " +
           "AND la.attemptTime > :since " +
           "AND la.successful = false")
    boolean hasRecentFailedAttempts(@Param("user") User user, @Param("since") Instant since);
    
    @Query("SELECT COUNT(la) FROM LoginAttempt la " +
           "WHERE la.user = :user " +
           "AND la.attemptTime > :since " +
           "AND la.successful = false")
    int countFailedAttemptsSince(@Param("user") User user, @Param("since") Instant since);
    
    Optional<LoginAttempt> findFirstByUserOrderByAttemptTimeDesc(@Param("user") User user);
}
