package com.twende.auth.repository;

import com.twende.auth.entity.OtpCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(String phoneNumber);

    void deleteByExpiresAtBefore(Instant cutoff);
}
