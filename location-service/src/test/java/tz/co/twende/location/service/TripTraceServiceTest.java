package tz.co.twende.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import tz.co.twende.location.entity.TripTrace;
import tz.co.twende.location.repository.TripTraceRepository;

@ExtendWith(MockitoExtension.class)
class TripTraceServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private TripTraceRepository tripTraceRepository;
    @Mock private ListOperations<String, Object> listOperations;

    @InjectMocks private TripTraceService tripTraceService;

    @Test
    void givenTracePoints_whenFlushTrace_thenSaveToDbAndDeleteRedisKey() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        List<Object> points =
                List.of(
                        "{\"lat\":-6.7924,\"lng\":39.2083,\"ts\":\"2025-01-15T10:30:00Z\"}",
                        "{\"lat\":-6.7930,\"lng\":39.2090,\"ts\":\"2025-01-15T10:30:03Z\"}");

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("ride:trace:" + rideId, 0, -1)).thenReturn(points);

        tripTraceService.flushTrace(rideId, driverId, "TZ", Instant.now(), Instant.now());

        ArgumentCaptor<TripTrace> captor = ArgumentCaptor.forClass(TripTrace.class);
        verify(tripTraceRepository).save(captor.capture());
        TripTrace saved = captor.getValue();
        assertThat(saved.getRideId()).isEqualTo(rideId);
        assertThat(saved.getDriverId()).isEqualTo(driverId);
        assertThat(saved.getTrace()).contains("-6.7924");
        assertThat(saved.getDistanceMetres()).isGreaterThan(0);
        verify(redisTemplate).delete("ride:trace:" + rideId);
    }

    @Test
    void givenNoTracePoints_whenFlushTrace_thenDoNotSave() {
        UUID rideId = UUID.randomUUID();
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("ride:trace:" + rideId, 0, -1)).thenReturn(List.of());

        tripTraceService.flushTrace(rideId, UUID.randomUUID(), "TZ", null, null);

        verify(tripTraceRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void givenSavedTrace_whenGetTrace_thenReturn() {
        UUID rideId = UUID.randomUUID();
        TripTrace trace = new TripTrace();
        trace.setRideId(rideId);
        when(tripTraceRepository.findByRideId(rideId)).thenReturn(Optional.of(trace));

        TripTrace result = tripTraceService.getTrace(rideId);
        assertThat(result).isNotNull();
        assertThat(result.getRideId()).isEqualTo(rideId);
    }
}
