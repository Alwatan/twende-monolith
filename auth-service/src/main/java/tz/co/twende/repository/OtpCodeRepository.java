package tz.co.twende.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.auth.entity.OtpCode;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc(String phoneNumber);

    void deleteByExpiresAtBefore(Instant cutoff);
}
