package tz.co.twende.matching.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
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
import tz.co.twende.matching.dto.DriverCandidate;
import tz.co.twende.matching.kafka.KafkaEventPublisher;
import tz.co.twende.matching.repository.OfferLogRepository;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private KafkaEventPublisher kafkaEventPublisher;
    @Mock private OfferLogRepository offerLogRepository;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks private BroadcastService broadcastService;

    @Test
    void givenNewCandidate_whenBroadcast_thenOfferSentAndLogged() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        DriverCandidate candidate =
                DriverCandidate.builder()
                        .driverId(driverId)
                        .distanceKm(new BigDecimal("1.5"))
                        .compositeScore(new BigDecimal("0.8"))
                        .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        broadcastService.broadcastOffers(
                rideId,
                riderId,
                "TZ",
                List.of(candidate),
                1,
                new BigDecimal("5000"),
                BigDecimal.ZERO,
                "TZS",
                "Kariakoo Market",
                new BigDecimal("3.5"),
                12);

        verify(kafkaEventPublisher).publishOfferNotification(any(), eq("TZ"), eq(riderId));
        verify(offerLogRepository).save(any());
    }

    @Test
    void givenDuplicateCandidate_whenBroadcast_thenOfferSkipped() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        DriverCandidate candidate =
                DriverCandidate.builder()
                        .driverId(driverId)
                        .distanceKm(new BigDecimal("1.0"))
                        .compositeScore(new BigDecimal("0.7"))
                        .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        broadcastService.broadcastOffers(
                rideId,
                riderId,
                "TZ",
                List.of(candidate),
                1,
                new BigDecimal("5000"),
                BigDecimal.ZERO,
                "TZS",
                "Msasani",
                new BigDecimal("2.0"),
                8);

        verify(kafkaEventPublisher, never()).publishOfferNotification(any(), any(), any());
        verify(offerLogRepository, never()).save(any());
    }

    @Test
    void givenMultipleCandidates_whenBroadcast_thenEachProcessed() {
        UUID rideId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        UUID driver1 = UUID.randomUUID();
        UUID driver2 = UUID.randomUUID();

        DriverCandidate c1 =
                DriverCandidate.builder()
                        .driverId(driver1)
                        .distanceKm(new BigDecimal("1.0"))
                        .compositeScore(new BigDecimal("0.9"))
                        .build();
        DriverCandidate c2 =
                DriverCandidate.builder()
                        .driverId(driver2)
                        .distanceKm(new BigDecimal("2.0"))
                        .compositeScore(new BigDecimal("0.7"))
                        .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        broadcastService.broadcastOffers(
                rideId,
                riderId,
                "TZ",
                List.of(c1, c2),
                1,
                new BigDecimal("3000"),
                BigDecimal.ZERO,
                "TZS",
                "Posta",
                new BigDecimal("4.0"),
                15);

        verify(kafkaEventPublisher, times(2))
                .publishOfferNotification(any(), eq("TZ"), eq(riderId));
        verify(offerLogRepository, times(2)).save(any());
    }
}
