package tz.co.twende.driver.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.driver.entity.DriverStatusLog;

@Repository
public interface DriverStatusLogRepository extends JpaRepository<DriverStatusLog, UUID> {

    List<DriverStatusLog> findByDriverIdOrderByChangedAtDesc(UUID driverId);
}
