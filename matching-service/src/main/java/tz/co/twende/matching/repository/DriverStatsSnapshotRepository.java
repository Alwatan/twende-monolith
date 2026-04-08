package tz.co.twende.matching.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.matching.entity.DriverStatsSnapshot;

@Repository
public interface DriverStatsSnapshotRepository extends JpaRepository<DriverStatsSnapshot, UUID> {

    Optional<DriverStatsSnapshot> findByDriverId(UUID driverId);
}
