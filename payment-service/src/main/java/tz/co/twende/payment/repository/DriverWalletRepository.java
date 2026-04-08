package tz.co.twende.payment.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.payment.entity.DriverWallet;

@Repository
public interface DriverWalletRepository extends JpaRepository<DriverWallet, UUID> {

    Optional<DriverWallet> findByDriverId(UUID driverId);
}
