package tz.co.twende.location.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.location.entity.TripTrace;
import tz.co.twende.location.repository.TripTraceRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripTraceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final TripTraceRepository tripTraceRepository;

    @Transactional
    public void flushTrace(
            UUID rideId,
            UUID driverId,
            String countryCode,
            Instant startedAt,
            Instant completedAt) {
        String key = "ride:trace:" + rideId;
        List<Object> rawPoints = redisTemplate.opsForList().range(key, 0, -1);

        if (rawPoints == null || rawPoints.isEmpty()) {
            log.warn("No trace points found for ride {}", rideId);
            return;
        }

        StringBuilder traceJson = new StringBuilder("[");
        for (int i = 0; i < rawPoints.size(); i++) {
            if (i > 0) traceJson.append(",");
            traceJson.append(rawPoints.get(i).toString());
        }
        traceJson.append("]");

        int totalDistance = calculateTotalDistance(rawPoints);

        TripTrace trace = new TripTrace();
        trace.setRideId(rideId);
        trace.setDriverId(driverId);
        trace.setCountryCode(countryCode);
        trace.setTrace(traceJson.toString());
        trace.setDistanceMetres(totalDistance);
        trace.setStartedAt(startedAt);
        trace.setCompletedAt(completedAt);
        tripTraceRepository.save(trace);

        redisTemplate.delete(key);
        log.info(
                "Flushed {} trace points for ride {} ({}m)",
                rawPoints.size(),
                rideId,
                totalDistance);
    }

    public TripTrace getTrace(UUID rideId) {
        return tripTraceRepository.findByRideId(rideId).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private int calculateTotalDistance(List<Object> rawPoints) {
        double totalMetres = 0;
        double prevLat = Double.NaN;
        double prevLng = Double.NaN;

        for (Object raw : rawPoints) {
            try {
                String point = raw.toString();
                // Simple JSON parse for {lat, lng, ts}
                double lat = extractDouble(point, "lat");
                double lng = extractDouble(point, "lng");
                if (!Double.isNaN(prevLat)) {
                    totalMetres += haversine(prevLat, prevLng, lat, lng);
                }
                prevLat = lat;
                prevLng = lng;
            } catch (Exception e) {
                log.debug("Skipping malformed trace point: {}", raw);
            }
        }
        return (int) Math.round(totalMetres);
    }

    private double extractDouble(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) {
            search = "\"" + key + "\" :";
            idx = json.indexOf(search);
        }
        if (idx < 0) return Double.NaN;
        int start = idx + search.length();
        int end = start;
        while (end < json.length()
                && (Character.isDigit(json.charAt(end))
                        || json.charAt(end) == '.'
                        || json.charAt(end) == '-')) {
            end++;
        }
        return Double.parseDouble(json.substring(start, end));
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000; // Earth radius in metres
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.sin(dLon / 2)
                                * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
