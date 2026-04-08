package tz.co.twende.payment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.payment.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReferenceIdAndReferenceType(UUID referenceId, String referenceType);

    List<Transaction> findByStatusAndInitiatedAtBefore(PaymentStatus status, Instant before);

    Page<Transaction> findByPayerId(UUID payerId, Pageable pageable);

    Page<Transaction> findByPayeeId(UUID payeeId, Pageable pageable);

    boolean existsByReferenceIdAndReferenceType(UUID referenceId, String referenceType);
}
