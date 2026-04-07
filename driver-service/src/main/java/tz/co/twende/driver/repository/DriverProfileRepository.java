package tz.co.twende.driver.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.driver.entity.DriverProfile;

@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {

    Page<DriverProfile> findByStatus(DriverStatus status, Pageable pageable);

    Page<DriverProfile> findByCountryCode(String countryCode, Pageable pageable);

    Page<DriverProfile> findByStatusAndCountryCode(
            DriverStatus status, String countryCode, Pageable pageable);
}
