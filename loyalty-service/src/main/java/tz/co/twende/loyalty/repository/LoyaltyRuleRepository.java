package tz.co.twende.loyalty.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.loyalty.entity.LoyaltyRule;

@Repository
public interface LoyaltyRuleRepository extends JpaRepository<LoyaltyRule, UUID> {

    Optional<LoyaltyRule> findByCountryCodeAndVehicleTypeAndIsActiveTrue(
            String countryCode, String vehicleType);

    List<LoyaltyRule> findByCountryCode(String countryCode);
}
