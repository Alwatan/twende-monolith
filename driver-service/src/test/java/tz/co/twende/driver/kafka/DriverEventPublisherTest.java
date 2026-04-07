package tz.co.twende.driver.kafka;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.event.driver.DriverStatusUpdatedEvent;
import tz.co.twende.driver.config.KafkaConfig;
import tz.co.twende.driver.entity.DriverProfile;

@ExtendWith(MockitoExtension.class)
class DriverEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private DriverEventPublisher publisher;

    @Test
    void givenStatusChange_whenPublishStatusUpdated_thenSendToKafka() {
        DriverProfile driver = createDriver(UUID.randomUUID());
        driver.setStatus(DriverStatus.ONLINE_AVAILABLE);

        publisher.publishStatusUpdated(driver, DriverStatus.APPROVED);

        ArgumentCaptor<DriverStatusUpdatedEvent> captor =
                ArgumentCaptor.forClass(DriverStatusUpdatedEvent.class);
        verify(kafkaTemplate)
                .send(
                        eq(KafkaConfig.TOPIC_DRIVERS_STATUS_UPDATED),
                        eq(driver.getId().toString()),
                        captor.capture());
        assertThat(captor.getValue().getDriverId()).isEqualTo(driver.getId());
        assertThat(captor.getValue().getPreviousStatus()).isEqualTo(DriverStatus.APPROVED);
        assertThat(captor.getValue().getNewStatus()).isEqualTo(DriverStatus.ONLINE_AVAILABLE);
    }

    @Test
    void givenApprovedDriver_whenPublishDriverApproved_thenSendToKafka() {
        DriverProfile driver = createDriver(UUID.randomUUID());
        driver.setStatus(DriverStatus.APPROVED);

        publisher.publishDriverApproved(driver);

        verify(kafkaTemplate)
                .send(
                        eq(KafkaConfig.TOPIC_DRIVERS_APPROVED),
                        eq(driver.getId().toString()),
                        any(DriverStatusUpdatedEvent.class));
    }

    private DriverProfile createDriver(UUID id) {
        DriverProfile driver = new DriverProfile();
        driver.setId(id);
        driver.setFullName("John Driver");
        driver.setCountryCode("TZ");
        driver.setStatus(DriverStatus.APPROVED);
        driver.setCreatedAt(Instant.now());
        driver.setUpdatedAt(Instant.now());
        return driver;
    }
}
