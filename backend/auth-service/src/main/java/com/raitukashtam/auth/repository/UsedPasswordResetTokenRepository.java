package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.UsedPasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsedPasswordResetTokenRepository extends JpaRepository<UsedPasswordResetToken, String> {
}
