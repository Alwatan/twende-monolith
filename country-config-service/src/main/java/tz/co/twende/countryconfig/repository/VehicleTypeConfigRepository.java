package tz.co.twende.countryconfig.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.countryconfig.entity.VehicleTypeConfig;

@Repository
public interface VehicleTypeConfigRepository extends JpaRepository<VehicleTypeConfig, UUID> {

    List<VehicleTypeConfig> findByCountryCode(String countryCode);

    List<VehicleTypeConfig> findByCountryCodeAndIsActiveTrue(String countryCode);

    Optional<VehicleTypeConfig> findByCountryCodeAndVehicleType(
            String countryCode, String vehicleType);
}
