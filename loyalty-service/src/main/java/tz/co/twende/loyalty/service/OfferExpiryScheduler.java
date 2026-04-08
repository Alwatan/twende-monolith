package tz.co.twende.loyalty.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.OfferStatus;
import tz.co.twende.loyalty.entity.FreeRideOffer;
import tz.co.twende.loyalty.repository.FreeRideOfferRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class OfferExpiryScheduler {

    private final FreeRideOfferRepository offerRepository;

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void expireOffers() {
        List<FreeRideOffer> expired =
                offerRepository.findByStatusAndExpiresAtBefore(
                        OfferStatus.AVAILABLE.name(), Instant.now());

        if (expired.isEmpty()) {
            return;
        }

        expired.forEach(o -> o.setStatus(OfferStatus.EXPIRED.name()));
        offerRepository.saveAll(expired);
        log.info("Expired {} free ride offers", expired.size());
    }
}
