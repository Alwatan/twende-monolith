package tz.co.twende.countryconfig.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.countryconfig.entity.FlatFeeConfig;

@Repository
public interface FlatFeeConfigRepository extends JpaRepository<FlatFeeConfig, UUID> {

    Optional<FlatFeeConfig> findByCountryCodeAndServiceCategoryAndActiveTrue(
            String countryCode, String serviceCategory);

    List<FlatFeeConfig> findByCountryCodeAndActiveTrue(String countryCode);

    List<FlatFeeConfig> findByCountryCode(String countryCode);
}
