package tz.co.twende.payment.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.payment.entity.CashDeclaration;

@Repository
public interface CashDeclarationRepository extends JpaRepository<CashDeclaration, UUID> {

    Optional<CashDeclaration> findByRideId(UUID rideId);

    boolean existsByRideId(UUID rideId);
}
