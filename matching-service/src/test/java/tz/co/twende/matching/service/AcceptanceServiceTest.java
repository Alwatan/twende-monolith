package tz.co.twende.matching.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tz.co.twende.matching.dto.AcceptRejectResponse;
import tz.co.twende.matching.entity.OfferLog;
import tz.co.twende.matching.kafka.KafkaEventPublisher;
import tz.co.twende.matching.repository.OfferLogRepository;

@ExtendWith(MockitoExtension.class)
class AcceptanceServiceTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private KafkaEventPublisher kafkaEventPublisher;
    @Mock private OfferLogRepository offerLogRepository;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private SetOperations<String, String> setOperations;

    @InjectMocks private AcceptanceService acceptanceService;

    @Test
    void givenNoExistingAcceptance_whenAcceptOffer_thenWinsRace() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                        eq("ride_accepted:" + rideId),
                        eq(driverId.toString()),
                        eq(Duration.ofSeconds(60))))
                .thenReturn(true);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(anyString(), eq("offered_count"))).thenReturn("5");
        when(hashOperations.get(anyString(), eq("accepted_count"))).thenReturn("3");
        when(offerLogRepository.findByRideIdAndDriverId(rideId, driverId))
                .thenReturn(Optional.of(new OfferLog()));

        AcceptRejectResponse result = acceptanceService.acceptOffer(rideId, driverId, "TZ");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAction()).isEqualTo("ACCEPT");
        assertThat(result.getMessage()).isEqualTo("Ride accepted successfully");
        verify(kafkaEventPublisher).publishOfferAccepted(rideId, driverId, "TZ", 300);
    }

    @Test
    void givenExistingAcceptance_whenAcceptOffer_thenLosesRace() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                        eq("ride_accepted:" + rideId),
                        eq(driverId.toString()),
                        eq(Duration.ofSeconds(60))))
                .thenReturn(false);
        when(offerLogRepository.findByRideIdAndDriverId(rideId, driverId))
                .thenReturn(Optional.empty());

        AcceptRejectResponse result = acceptanceService.acceptOffer(rideId, driverId, "TZ");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Ride already accepted by another driver");
        verify(kafkaEventPublisher, never()).publishOfferAccepted(any(), any(), any(), anyInt());
    }

    @Test
    void givenDriverRejects_whenRejectOffer_thenAddedToRejectedSet() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("driver_rejected:" + rideId))
                .thenReturn(Set.of(driverId.toString()));
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(anyString(), eq("offered_count"))).thenReturn("5");
        when(hashOperations.get(anyString(), eq("accepted_count"))).thenReturn("2");
        when(offerLogRepository.findByRideIdAndDriverId(rideId, driverId))
                .thenReturn(Optional.of(new OfferLog()));

        AcceptRejectResponse result = acceptanceService.rejectOffer(rideId, driverId, "TZ");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAction()).isEqualTo("REJECT");
        verify(setOperations).add("driver_rejected:" + rideId, driverId.toString());
        verify(kafkaEventPublisher)
                .publishDriverRejected(eq(rideId), eq(driverId), eq("TZ"), eq(1));
    }

    @Test
    void givenMultipleRejections_whenRejectOffer_thenRejectionCountReflectsAll() {
        UUID rideId = UUID.randomUUID();
        UUID driverId1 = UUID.randomUUID();
        UUID driverId2 = UUID.randomUUID();

        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("driver_rejected:" + rideId))
                .thenReturn(Set.of(driverId1.toString(), driverId2.toString()));
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(anyString(), eq("offered_count"))).thenReturn("10");
        when(hashOperations.get(anyString(), eq("accepted_count"))).thenReturn("3");
        when(offerLogRepository.findByRideIdAndDriverId(rideId, driverId2))
                .thenReturn(Optional.empty());

        AcceptRejectResponse result = acceptanceService.rejectOffer(rideId, driverId2, "TZ");

        assertThat(result.isSuccess()).isTrue();
        verify(kafkaEventPublisher)
                .publishDriverRejected(eq(rideId), eq(driverId2), eq("TZ"), eq(2));
    }

    @Test
    void givenAcceptance_whenRecomputeRate_thenRateUpdated() {
        UUID driverId = UUID.randomUUID();

        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("driver_stats:" + driverId, "offered_count")).thenReturn("10");
        when(hashOperations.get("driver_stats:" + driverId, "accepted_count")).thenReturn("7");

        acceptanceService.recomputeAcceptanceRate(driverId);

        verify(hashOperations)
                .put("driver_stats:" + driverId, "acceptance_rate", String.valueOf(0.7));
    }
}
