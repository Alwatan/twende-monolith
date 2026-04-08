package tz.co.twende.subscription.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.subscription.entity.SubscriptionPlan;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    List<SubscriptionPlan> findByCountryCodeAndVehicleTypeAndIsActiveTrue(
            String countryCode, String vehicleType);

    List<SubscriptionPlan> findByCountryCodeAndIsActiveTrue(String countryCode);
}
