package tz.co.twende.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryScheduler {

    private final SubscriptionService subscriptionService;

    @Scheduled(fixedDelay = 600_000)
    public void expireSubscriptions() {
        log.debug("Running subscription expiry check");
        subscriptionService.expireSubscriptions();
    }
}
