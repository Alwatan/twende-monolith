package tz.co.twende.countryconfig.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.countryconfig.entity.OperatingCity;

@Repository
public interface OperatingCityRepository extends JpaRepository<OperatingCity, UUID> {

    List<OperatingCity> findByCountryCode(String countryCode);

    Optional<OperatingCity> findByCountryCodeAndCityId(String countryCode, String cityId);
}
