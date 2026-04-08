package tz.co.twende.subscription.service;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpirySchedulerTest {

    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private ExpiryScheduler expiryScheduler;

    @Test
    void whenSchedulerRuns_thenCallsExpireSubscriptions() {
        expiryScheduler.expireSubscriptions();

        verify(subscriptionService).expireSubscriptions();
    }
}
