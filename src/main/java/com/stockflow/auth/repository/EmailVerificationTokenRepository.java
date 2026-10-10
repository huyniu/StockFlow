package com.stockflow.auth.repository;

import com.stockflow.auth.domain.EmailVerificationToken;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);
}
