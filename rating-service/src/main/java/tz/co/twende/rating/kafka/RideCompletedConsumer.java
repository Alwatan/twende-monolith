package tz.co.twende.rating.kafka;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.rating.dto.RideDetailsDto;

@Component
@Slf4j
public class RideCompletedConsumer {

    private final ConcurrentHashMap<UUID, RideDetailsDto> rideCache = new ConcurrentHashMap<>();

    @KafkaListener(topics = "twende.rides.completed", groupId = "${spring.kafka.consumer.group-id}")
    public void onRideCompleted(RideCompletedEvent event) {
        log.debug("Received RideCompletedEvent for ride {}", event.getRideId());
        RideDetailsDto details = new RideDetailsDto();
        details.setId(event.getRideId());
        details.setRiderId(event.getRiderId());
        details.setDriverId(event.getDriverId());
        details.setStatus("COMPLETED");
        details.setCountryCode(event.getCountryCode());
        details.setCompletedAt(event.getCompletedAt());
        rideCache.put(event.getRideId(), details);
    }

    public RideDetailsDto getCachedRide(UUID rideId) {
        return rideCache.get(rideId);
    }

    public void evict(UUID rideId) {
        rideCache.remove(rideId);
    }
}
