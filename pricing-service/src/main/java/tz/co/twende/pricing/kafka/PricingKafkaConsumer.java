package tz.co.twende.pricing.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.pricing.client.CountryConfigClient;

@Component
public class PricingKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PricingKafkaConsumer.class);

    private final CountryConfigClient countryConfigClient;

    public PricingKafkaConsumer(CountryConfigClient countryConfigClient) {
        this.countryConfigClient = countryConfigClient;
    }

    @KafkaListener(topics = "twende.config.country-updated", groupId = "pricing-service")
    public void onCountryConfigUpdated(java.util.Map<String, Object> event) {
        String countryCode = (String) event.get("countryCode");
        if (countryCode != null) {
            log.info(
                    "Received country config update for {}, evicting pricing config cache",
                    countryCode);
            countryConfigClient.evictCache(countryCode);
        } else {
            log.warn("Received country config update without countryCode, ignoring");
        }
    }
}
