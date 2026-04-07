package tz.co.twende.countryconfig.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.countryconfig.entity.PaymentMethodConfig;

@Repository
public interface PaymentMethodConfigRepository extends JpaRepository<PaymentMethodConfig, UUID> {

    List<PaymentMethodConfig> findByCountryCode(String countryCode);

    List<PaymentMethodConfig> findByCountryCodeAndIsEnabledTrue(String countryCode);
}
