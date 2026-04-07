package tz.co.twende.driver.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.driver.entity.DriverVehicle;

@Repository
public interface DriverVehicleRepository extends JpaRepository<DriverVehicle, UUID> {

    List<DriverVehicle> findByDriverId(UUID driverId);

    boolean existsByDriverIdAndIsActiveTrue(UUID driverId);

    Optional<DriverVehicle> findByDriverIdAndIsActiveTrue(UUID driverId);

    boolean existsByDriverIdAndPlateNumber(UUID driverId, String plateNumber);
}
