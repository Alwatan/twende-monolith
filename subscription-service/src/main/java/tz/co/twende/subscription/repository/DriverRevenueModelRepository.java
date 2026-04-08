package tz.co.twende.subscription.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.subscription.entity.DriverRevenueModel;

@Repository
public interface DriverRevenueModelRepository extends JpaRepository<DriverRevenueModel, UUID> {

    Optional<DriverRevenueModel> findByDriverId(UUID driverId);

    boolean existsByDriverId(UUID driverId);
}
