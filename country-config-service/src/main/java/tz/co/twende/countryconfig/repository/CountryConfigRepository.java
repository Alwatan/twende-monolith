package tz.co.twende.countryconfig.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.countryconfig.entity.CountryConfig;
import tz.co.twende.countryconfig.entity.CountryConfig.CountryStatus;

@Repository
public interface CountryConfigRepository extends JpaRepository<CountryConfig, String> {

    Optional<CountryConfig> findByCode(String code);

    List<CountryConfig> findByStatus(CountryStatus status);
}
