# CLAUDE.md — matching-service

> Broadcast-and-accept driver matching engine for the Twende ride-hailing platform.
> Read this fully before writing any code in this module.

---

## 1. Overview

The matching-service is responsible for finding nearby drivers, scoring them, broadcasting
ride offers, and resolving the acceptance race atomically. It replaces the previous
deterministic assignment model where the engine picked the best driver and auto-assigned.

In the broadcast-and-accept model:
- The offer is sent to multiple drivers simultaneously
- Each driver has a 15-second window to ACCEPT or REJECT
- The first driver to ACCEPT wins via a Redis SETNX atomic lock
- Drivers who IGNORE (timeout) are treated as a soft reject for that ride only
- If all reject/timeout, the radius expands and the next batch is offered
- After 3 minutes total with no acceptance: `NoDriverFoundEvent`

This model is the prerequisite for fare boost (rider increases offer), rejection counter
(rider sees how many drivers passed), and trip start OTP features to work.

**Port:** 8086
**Database:** `twende_matching` (minimal -- mostly Redis-based)
**No Eureka. No Feign. No Config Server.** Uses Spring `RestClient` for all inter-service calls.

---

## 2. Package Structure

```
com.twende.matching
├── MatchingServiceApplication.java
├── config/
│   ├── SecurityConfig.java           # Resource server JWT validation
│   ├── RedisConfig.java              # RedisTemplate beans
│   ├── KafkaConfig.java              # Consumer + producer configuration
│   └── RestClientConfig.java         # RestClient beans for inter-service calls
├── service/
│   ├── MatchingService.java          # Core matching logic: find, score, broadcast
│   ├── DriverScoringService.java     # Distance + rating + acceptance rate scoring
│   ├── OfferBroadcastService.java    # Send offers to candidates, dedup via Redis
│   ├── AcceptanceService.java        # Atomic accept race via SETNX
│   ├── ExpansionScheduler.java       # @Scheduled batch expansion every 30s
│   └── DriverStatsService.java       # Acceptance rate tracking in Redis
├── controller/
│   └── DriverActionController.java   # Internal endpoints: accept/reject
├── consumer/
│   ├── RideRequestedConsumer.java    # Kafka: twende.rides.requested
│   ├── RideFareBoostedConsumer.java  # Kafka: twende.rides.fare-boosted
│   ├── RideCancelledConsumer.java    # Kafka: twende.rides.cancelled
│   └── DriverStatusConsumer.java     # Kafka: twende.drivers.status-updated
├── client/
│   ├── LocationServiceClient.java   # RestClient to location-service (geofence, nearby drivers)
│   ├── DriverServiceClient.java     # RestClient to driver-service (driver status, vehicle)
│   ├── RatingServiceClient.java     # RestClient to rating-service (driver rating)
│   └── RideServiceClient.java       # RestClient to ride-service (active rides for expansion)
├── dto/
│   ├── DriverCandidate.java         # Scored driver with distance, rating, acceptance rate
│   ├── OfferPayload.java            # Data sent to driver via push notification
│   ├── AcceptRequest.java
│   ├── RejectRequest.java
│   └── ServiceAreaCheckResult.java
└── event/
    └── KafkaEventPublisher.java     # Publishes matching events to Kafka
```

---

## 3. Database Schema

The matching-service has a minimal database. Most state lives in Redis. The database stores
only audit records that must survive Redis eviction.

```sql
-- V1__create_matching_schema.sql

-- Audit log of all offers sent to drivers (for analytics and debugging)
CREATE TABLE offer_logs (
    id            UUID PRIMARY KEY,
    ride_id       UUID        NOT NULL,
    driver_id     UUID        NOT NULL,
    country_code  CHAR(2)     NOT NULL,
    batch_number  INT         NOT NULL,
    distance_km   NUMERIC(6,2),
    score         NUMERIC(5,3),
    offered_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at  TIMESTAMPTZ,
    response      VARCHAR(20)  -- ACCEPTED, REJECTED, TIMEOUT, RIDE_TAKEN
);

CREATE INDEX idx_offer_logs_ride ON offer_logs(ride_id);
CREATE INDEX idx_offer_logs_driver ON offer_logs(driver_id);
CREATE INDEX idx_offer_logs_offered ON offer_logs(offered_at);

-- Persistent driver stats snapshot (synced from Redis daily)
CREATE TABLE driver_stats_snapshot (
    driver_id       UUID PRIMARY KEY,
    offered_count   INT NOT NULL DEFAULT 0,
    accepted_count  INT NOT NULL DEFAULT 0,
    rejection_count INT NOT NULL DEFAULT 0,
    acceptance_rate NUMERIC(5,3) NOT NULL DEFAULT 0.0,
    snapshot_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 4. Matching Flow

```
1. Consume RideRequestedEvent from Kafka (twende.rides.requested)

2. SERVICE AREA VALIDATION (before any matching)
   - Call location-service: GET /internal/geofence/check?lat=&lng=&countryCode=
   - Verify pickup is inside an OPERATING zone
   - Verify pickup is NOT in a RESTRICTED zone
   - Resolve cityId from pickup coordinates
   - If validation fails: publish error event back to ride-service, stop

3. FIND CANDIDATES
   - Call location-service: GET /internal/drivers/nearby?cc={cc}&vehicleType={vt}&lat={lat}&lng={lng}&radiusKm=3&limit=15
     (location-service runs GEORADIUS on drivers:{cc}:{vehicleType})
   - Filter: driver status == ONLINE_AVAILABLE, no active reservation
   - Score by: distance (50%) + rating (30%) + acceptance_rate (20%)
   - Take top 10 after scoring

4. BROADCAST (fire-and-forget per driver)
   For each of the top 10 candidates:
     a. Attempt SETNX driver_offered:{driverId}:{rideId} = "1" EX 20
        (prevents same driver being offered same ride twice)
     b. If SETNX returned true:
        - SADD rides_offered_to:{rideId} driverId
        - EXPIRE rides_offered_to:{rideId} 300
        - HINCRBY driver_stats:{driverId} offered_count 1
        - Publish DriverOfferNotificationEvent to Kafka
        - Save offer_logs record

5. WAIT (non-blocking -- Kafka consumer handles responses)

6. ON DRIVER_ACCEPT (via PUT /internal/driver-actions/{rideId}/accept):
   a. Try SETNX ride_accepted:{rideId} = driverId EX 60
      (atomic -- only first accept wins; others return false)
   b. If won the lock:
      - Publish RideOfferAcceptedEvent to Kafka -> ride-service assigns driver
      - Update offer_logs: response = ACCEPTED
      - HINCRBY driver_stats:{driverId} accepted_count 1
      - Recompute and HSET driver_stats:{driverId} acceptance_rate
   c. If lost the lock:
      - Return "Ride already accepted" to the caller
      - Update offer_logs: response = RIDE_TAKEN

7. ON DRIVER_REJECT (via PUT /internal/driver-actions/{rideId}/reject):
   - SADD driver_rejected:{rideId} driverId
   - EXPIRE driver_rejected:{rideId} 300
   - HINCRBY driver_stats:{driverId} rejection_count 1
   - Recompute and HSET driver_stats:{driverId} acceptance_rate
   - Update offer_logs: response = REJECTED
   - Publish DriverRejectedRideEvent to Kafka -> ride-service increments rejection_count

8. EXPANSION SCHEDULER (@Scheduled every 30s):
   - Query ride-service for rides in REQUESTED status
   - For each ride, read ride_offer_batches:{rideId} (current batch number)
   - Batch 1 (0-60s):   3km radius, top 10 -- already done by initial broadcast
   - Batch 2 (61-120s): 5km radius, next 10 not yet offered or rejected
   - Batch 3 (121-180s): 10km radius, next 10 not yet offered or rejected
   - After 180s: publish NoDriverFoundEvent to Kafka
   - INCR ride_offer_batches:{rideId} after each expansion
   - Filter candidates: NOT in rides_offered_to:{rideId} AND NOT in driver_rejected:{rideId}

9. RE-BROADCAST ON FARE BOOST (RideFareBoostedEvent from Kafka):
   - Find all ONLINE_AVAILABLE drivers not yet offered this ride
     (check SISMEMBER rides_offered_to:{rideId} for each candidate)
   - Exclude drivers in driver_rejected:{rideId} (they made their choice)
   - Send fresh offer with updated fare amount
   - Existing offered drivers who haven't responded yet see the original fare
     (their offer window is only 15s anyway)
```

---

## 5. Driver Scoring Algorithm

```java
public double scoreCandidate(DriverCandidate candidate) {
    // Distance score: closer is better (inverse, normalised 0-1)
    // 0 km = 1.0, radiusKm = 0.0
    double distanceScore = 1.0 - (candidate.getDistanceKm() / radiusKm);
    distanceScore = Math.max(0.0, distanceScore);

    // Rating score: normalised 0-1 (rating is 1-5 scale)
    double ratingScore = (candidate.getRating() - 1.0) / 4.0;

    // Acceptance rate score: 0-1 (already a ratio)
    double acceptanceScore = candidate.getAcceptanceRate();

    // Weighted sum
    return (distanceScore * 0.50)
         + (ratingScore * 0.30)
         + (acceptanceScore * 0.20);
}
```

**Weights:**
- Distance: 50% -- proximity is the strongest predictor of fast pickup
- Rating: 30% -- higher-rated drivers provide better experience
- Acceptance rate: 20% -- drivers who accept more often are more reliable

**Acceptance rate deprioritization:**
Drivers with acceptance rate below 30% are deprioritized (scored lower) but NOT blocked.
Blocking low-acceptance drivers would reduce supply, which hurts riders.

---

## 6. Redis Keys Reference

| Key | Type | TTL | Purpose |
|---|---|---|---|
| `ride_accepted:{rideId}` | String | 60s | Atomic lock -- first driver to ACCEPT wins via SETNX |
| `driver_offered:{driverId}:{rideId}` | String | 20s | Dedup -- prevents duplicate offers to same driver for same ride |
| `rides_offered_to:{rideId}` | Set | 300s | All driver IDs that have been offered this ride |
| `driver_rejected:{rideId}` | Set | 300s | Driver IDs that explicitly rejected this ride |
| `ride_offer_batches:{rideId}` | String (int) | 300s | Current expansion batch number (1, 2, or 3) |
| `driver_stats:{driverId}` | Hash | none | Acceptance rate tracking: offered_count, accepted_count, rejection_count, acceptance_rate |

### Redis Operations in Detail

**Broadcasting an offer to a driver:**
```java
// 1. Dedup check -- only offer if not already offered
Boolean isNew = redisTemplate.opsForValue()
    .setIfAbsent("driver_offered:" + driverId + ":" + rideId, "1",
        Duration.ofSeconds(20));

if (Boolean.TRUE.equals(isNew)) {
    // 2. Track who has been offered
    redisTemplate.opsForSet().add("rides_offered_to:" + rideId, driverId.toString());
    redisTemplate.expire("rides_offered_to:" + rideId, 300, TimeUnit.SECONDS);

    // 3. Update driver stats
    redisTemplate.opsForHash().increment("driver_stats:" + driverId, "offered_count", 1);

    // 4. Publish offer notification event to Kafka
    kafkaEventPublisher.publishOfferNotification(rideId, driverId, offerPayload);
}
```

**Acceptance race (atomic lock):**
```java
public boolean tryAcceptRide(UUID rideId, UUID driverId) {
    Boolean won = redisTemplate.opsForValue()
        .setIfAbsent("ride_accepted:" + rideId, driverId.toString(),
            Duration.ofSeconds(60));
    return Boolean.TRUE.equals(won);
}
```

**Recording a rejection:**
```java
public void handleDriverReject(UUID rideId, UUID driverId) {
    // Track rejection for this ride (prevents re-offer, even on fare boost)
    redisTemplate.opsForSet().add("driver_rejected:" + rideId, driverId.toString());
    redisTemplate.expire("driver_rejected:" + rideId, 300, TimeUnit.SECONDS);

    // Update driver stats
    redisTemplate.opsForHash().increment("driver_stats:" + driverId, "rejection_count", 1);
    recomputeAcceptanceRate(driverId);

    // Publish event to ride-service
    kafkaEventPublisher.publishDriverRejected(rideId, driverId);
}
```

**Filtering candidates for expansion / re-broadcast:**
```java
public List<String> filterNewCandidates(UUID rideId, List<String> candidates) {
    Set<Object> alreadyOffered = redisTemplate.opsForSet()
        .members("rides_offered_to:" + rideId);
    Set<Object> rejected = redisTemplate.opsForSet()
        .members("driver_rejected:" + rideId);

    return candidates.stream()
        .filter(id -> !alreadyOffered.contains(id))
        .filter(id -> !rejected.contains(id))
        .toList();
}
```

**Acceptance rate recomputation:**
```java
private void recomputeAcceptanceRate(UUID driverId) {
    String key = "driver_stats:" + driverId;
    Map<Object, Object> stats = redisTemplate.opsForHash().entries(key);
    long offered = Long.parseLong(stats.getOrDefault("offered_count", "0").toString());
    long accepted = Long.parseLong(stats.getOrDefault("accepted_count", "0").toString());
    double rate = offered > 0 ? (double) accepted / offered : 0.0;
    redisTemplate.opsForHash().put(key, "acceptance_rate", String.valueOf(rate));
}
```

---

## 7. Expansion Scheduler

```java
@Component
public class ExpansionScheduler {

    @Scheduled(fixedDelay = 30_000)
    public void expandMatchingRadius() {
        // 1. Query ride-service for rides in REQUESTED status
        List<ActiveRide> requestedRides = rideServiceClient.getRequestedRides();

        for (ActiveRide ride : requestedRides) {
            long ageSeconds = Duration.between(ride.getRequestedAt(), Instant.now()).toSeconds();
            String batchKey = "ride_offer_batches:" + ride.getId();
            int currentBatch = getOrDefault(batchKey, 1);

            if (ageSeconds > 180) {
                // All batches exhausted -- no driver found
                kafkaEventPublisher.publishNoDriverFound(ride.getId(), ride.getCountryCode());
                cleanupRedisKeys(ride.getId());
                continue;
            }

            int targetBatch;
            double radiusKm;
            if (ageSeconds <= 60) {
                targetBatch = 1;
                radiusKm = 3.0;
            } else if (ageSeconds <= 120) {
                targetBatch = 2;
                radiusKm = 5.0;
            } else {
                targetBatch = 3;
                radiusKm = 10.0;
            }

            // Only expand if we haven't done this batch yet
            if (currentBatch < targetBatch) {
                List<String> candidates = locationServiceClient.findNearbyDrivers(
                    ride.getCountryCode(), ride.getVehicleType(),
                    ride.getPickupLat(), ride.getPickupLng(), radiusKm, 15);

                // Filter out already-offered and rejected drivers
                List<String> newCandidates = filterNewCandidates(ride.getId(), candidates);

                // Score and take top 10
                List<DriverCandidate> scored = scoreCandidates(newCandidates, ride);
                List<DriverCandidate> top10 = scored.stream()
                    .sorted(Comparator.comparingDouble(DriverCandidate::getScore).reversed())
                    .limit(10)
                    .toList();

                broadcastOffers(ride, top10, targetBatch);

                // Update batch counter
                redisTemplate.opsForValue().set(batchKey, String.valueOf(targetBatch));
                redisTemplate.expire(batchKey, 300, TimeUnit.SECONDS);
            }
        }
    }
}
```

### Expansion Timeline

| Time Window | Batch | Radius | Max Drivers | Action |
|---|---|---|---|---|
| 0-60s | 1 | 3 km | 10 | Initial broadcast (triggered by RideRequestedEvent) |
| 61-120s | 2 | 5 km | 10 (new only) | Expansion -- skip already offered and rejected |
| 121-180s | 3 | 10 km | 10 (new only) | Final expansion |
| > 180s | -- | -- | -- | Publish NoDriverFoundEvent, cleanup Redis keys |

---

## 8. Service Area Validation

Before any matching begins, the matching-service validates the ride's pickup location.
This is done via RestClient calls to location-service.

```java
public ServiceAreaCheckResult validateServiceArea(String countryCode, BigDecimal lat, BigDecimal lng) {
    // 1. Check if pickup is inside an OPERATING zone
    // GET {location-service}/internal/geofence/check?lat={lat}&lng={lng}&countryCode={countryCode}&type=OPERATING
    boolean inServiceArea = locationServiceClient.isInServiceArea(countryCode, lat, lng);
    if (!inServiceArea) {
        return ServiceAreaCheckResult.rejected("Pickup location is outside our service area");
    }

    // 2. Check if pickup is in a RESTRICTED zone
    // GET {location-service}/internal/geofence/check?lat={lat}&lng={lng}&countryCode={countryCode}&type=RESTRICTED
    boolean inRestricted = locationServiceClient.isInRestrictedZone(countryCode, lat, lng);
    if (inRestricted) {
        return ServiceAreaCheckResult.rejected("Pickup location is in a restricted area");
    }

    // 3. Resolve cityId from pickup coordinates
    // GET {location-service}/internal/cities/resolve?lat={lat}&lng={lng}&countryCode={countryCode}
    UUID cityId = locationServiceClient.resolveCityId(countryCode, lat, lng);

    return ServiceAreaCheckResult.accepted(cityId);
}
```

If validation fails, the matching-service publishes an error event back to ride-service
and does NOT proceed to the broadcast phase.

---

## 9. API Endpoints

### Internal (called by ride-service, not routed through API gateway)

| Method | Path | Description |
|---|---|---|
| `PUT` | `/internal/driver-actions/{rideId}/accept` | Driver accepts a ride offer |
| `PUT` | `/internal/driver-actions/{rideId}/reject` | Driver explicitly rejects a ride offer |

These endpoints are called by ride-service after it validates the driver's JWT and confirms
the ride is still in `REQUESTED` status.

**Accept request:**
```json
PUT /internal/driver-actions/{rideId}/accept
{
  "driverId": "550e8400-e29b-41d4-a716-446655440000"
}
// Success: 200 OK { "success": true, "data": { "accepted": true } }
// Lost race: 200 OK { "success": true, "data": { "accepted": false, "reason": "Ride already accepted" } }
```

**Reject request:**
```json
PUT /internal/driver-actions/{rideId}/reject
{
  "driverId": "550e8400-e29b-41d4-a716-446655440000"
}
// Response: 200 OK { "success": true }
```

---

## 10. Driver Offer Notification Payload

The push notification the driver receives must include enough information to decide without
opening the full app. This is published to Kafka topic `twende.drivers.offer-notification`
and consumed by notification-service to send via FCM.

```java
public class DriverOfferNotificationEvent extends KafkaEvent {
    private UUID rideId;
    private UUID driverId;
    private String riderId;

    // What the driver sees on their lock screen / offer card
    private double pickupDistanceKm;           // "0.8 km away"
    private String pickupAreaName;             // "Kariakoo Market"
    private double estimatedTripDistanceKm;
    private int estimatedTripMinutes;

    private BigDecimal totalFare;              // base + boost
    private BigDecimal boostAmount;            // 0 if no boost, > 0 shows "Boosted!" badge
    private String currencyCode;

    private int offerWindowSeconds;            // 15 -- countdown timer in driver app
}
```

The driver app renders an offer card with a 15-second countdown. The driver taps Accept or
Reject. If the window expires with no action, it is treated as a soft reject -- the driver
is NOT re-offered for this ride, but no DriverRejectedRideEvent is published and the
rejection counter is NOT incremented.

---

## 11. Kafka Topics

### Consumed

| Topic | Event | Action |
|---|---|---|
| `twende.rides.requested` | `RideRequestedEvent` | Validate service area, trigger initial broadcast (batch 1, 3km) |
| `twende.rides.fare-boosted` | `RideFareBoostedEvent` | Re-broadcast to un-offered drivers with updated fare; skip rejected drivers |
| `twende.rides.cancelled` | `RideCancelledEvent` | Clean up all Redis keys for the ride, notify offered drivers |
| `twende.drivers.status-updated` | `DriverStatusUpdatedEvent` | Update local driver availability knowledge |

### Published

| Topic | Event | Trigger |
|---|---|---|
| `twende.drivers.offer-notification` | `DriverOfferNotificationEvent` | Sending offer to a driver (consumed by notification-service for FCM push) |
| `twende.rides.offer-accepted` | `RideOfferAcceptedEvent` | Driver accepted and won the SETNX lock |
| `twende.drivers.rejected-ride` | `DriverRejectedRideEvent` | Driver explicitly rejected an offer |
| `twende.rides.no-driver-found` | `NoDriverFoundEvent` | All 3 expansion batches exhausted with no acceptance |

### Event Payloads

**RideOfferAcceptedEvent:**
```json
{
  "rideId": "...",
  "driverId": "...",
  "estimatedArrivalSeconds": 240,
  "countryCode": "TZ"
}
```

**DriverRejectedRideEvent:**
```json
{
  "rideId": "...",
  "driverId": "...",
  "newRejectionCount": 3,
  "countryCode": "TZ"
}
```

**NoDriverFoundEvent:**
```json
{
  "rideId": "...",
  "countryCode": "TZ",
  "totalDriversOffered": 25,
  "totalRejections": 8,
  "searchDurationSeconds": 183
}
```

---

## 12. Acceptance Rate Tracking

Driver acceptance rates are tracked in a Redis hash and affect matching scores.

**Redis key:** `driver_stats:{driverId}`

| Hash Field | Type | Description |
|---|---|---|
| `offered_count` | int | Total offers sent to this driver |
| `accepted_count` | int | Total offers this driver accepted |
| `rejection_count` | int | Total offers this driver explicitly rejected |
| `acceptance_rate` | double | `accepted_count / offered_count` (recomputed on each update) |

**Update triggers:**
- `offered_count` incremented on each offer broadcast
- `accepted_count` incremented when driver wins the acceptance race
- `rejection_count` incremented on explicit reject (NOT on timeout/ignore)
- `acceptance_rate` recomputed after every accepted_count or rejection_count change

**Persistence:** A `@Scheduled` job runs daily to snapshot `driver_stats:{driverId}`
from Redis to the `driver_stats_snapshot` table. This provides durability and analytics
data that survives Redis restarts.

**Score impact:** Drivers with acceptance rate < 30% are deprioritized in scoring but
NOT blocked from receiving offers. Blocking would reduce driver supply.

---

## 13. Re-Broadcast on Fare Boost

When a rider boosts the fare while the ride is in `REQUESTED` status, ride-service publishes
`RideFareBoostedEvent` to Kafka. The matching-service handles it:

```java
@KafkaListener(topics = "twende.rides.fare-boosted")
public void onFareBoosted(RideFareBoostedEvent event) {
    UUID rideId = event.getRideId();

    // 1. Find nearby ONLINE_AVAILABLE drivers
    List<String> candidates = locationServiceClient.findNearbyDrivers(
        event.getCountryCode(), ride.getVehicleType(),
        ride.getPickupLat(), ride.getPickupLng(), 5.0, 15);

    // 2. Exclude already-offered drivers
    Set<Object> alreadyOffered = redisTemplate.opsForSet()
        .members("rides_offered_to:" + rideId);

    // 3. Exclude drivers who explicitly rejected (they made their choice)
    Set<Object> rejected = redisTemplate.opsForSet()
        .members("driver_rejected:" + rideId);

    List<String> newCandidates = candidates.stream()
        .filter(id -> !alreadyOffered.contains(id))
        .filter(id -> !rejected.contains(id))
        .toList();

    // 4. Score and broadcast to new candidates with updated fare
    List<DriverCandidate> scored = scoreCandidates(newCandidates, ride);
    broadcastOffers(ride, scored, getCurrentBatch(rideId));
}
```

**Critical rule:** Drivers who already rejected are NEVER re-offered, even with a boosted
fare. Their rejection is permanent for that ride. This is enforced by checking the
`driver_rejected:{rideId}` Redis set.

---

## 14. Ride Cancellation Cleanup

When a ride is cancelled (via `RideCancelledEvent`), all Redis keys for that ride must
be cleaned up:

```java
@KafkaListener(topics = "twende.rides.cancelled")
public void onRideCancelled(RideCancelledEvent event) {
    UUID rideId = event.getRideId();

    // Clean up all Redis keys
    redisTemplate.delete("ride_accepted:" + rideId);
    redisTemplate.delete("rides_offered_to:" + rideId);
    redisTemplate.delete("driver_rejected:" + rideId);
    redisTemplate.delete("ride_offer_batches:" + rideId);

    // Delete dedup keys for all offered drivers
    Set<Object> offeredDrivers = redisTemplate.opsForSet()
        .members("rides_offered_to:" + rideId);
    if (offeredDrivers != null) {
        for (Object driverId : offeredDrivers) {
            redisTemplate.delete("driver_offered:" + driverId + ":" + rideId);
        }
    }

    // Notify offered drivers that the ride was cancelled
    // (publish notification events to Kafka)
}
```

---

## 15. Inter-Service Communication

All synchronous inter-service calls use Spring `RestClient`. Base URLs are configured
via environment variables. No Eureka, no Feign.

| Target Service | Base URL Env Var | Purpose |
|---|---|---|
| location-service | `LOCATION_SERVICE_URL` | Nearby driver queries (Redis GEO), geofence checks, city resolution |
| driver-service | `DRIVER_SERVICE_URL` | Driver status, vehicle type, availability checks |
| rating-service | `RATING_SERVICE_URL` | Driver average rating for scoring |
| ride-service | `RIDE_SERVICE_URL` | Active REQUESTED rides for expansion scheduler |

```java
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient locationServiceClient(@Value("${twende.services.location-url}") String baseUrl) {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json")
            .build();
    }

    // Same pattern for driver-service, rating-service, ride-service
}
```

---

## 16. Application Configuration

```yaml
server:
  port: 8086

spring:
  application:
    name: matching-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_matching
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      password: ${REDIS_PASSWORD:}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: matching-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.twende.*
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

twende:
  matching:
    initial-radius-km: 3.0
    expansion-radii-km: [5.0, 10.0]
    max-candidates-per-batch: 10
    candidate-fetch-limit: 15
    offer-window-seconds: 15
    max-search-duration-seconds: 180
    expansion-interval-ms: 30000
    scoring:
      distance-weight: 0.50
      rating-weight: 0.30
      acceptance-rate-weight: 0.20
      low-acceptance-threshold: 0.30
    stats-snapshot-cron: "0 0 3 * * *"  # daily at 3 AM
  services:
    location-url: ${LOCATION_SERVICE_URL:http://localhost:8087}
    driver-url: ${DRIVER_SERVICE_URL:http://localhost:8084}
    rating-url: ${RATING_SERVICE_URL:http://localhost:8092}
    ride-url: ${RIDE_SERVICE_URL:http://localhost:8085}
```

---

## 17. Important Rules

1. **Broadcast-and-accept, NOT deterministic assignment.** The matching-service does NOT
   pick a single driver and assign them. It broadcasts to multiple drivers and the first
   to accept wins. The previous deterministic model is obsolete.

2. **SETNX is the only acceptance mechanism.** The Redis `SETNX` (setIfAbsent) on
   `ride_accepted:{rideId}` is the sole arbiter of the acceptance race. There is no
   database lock, no distributed lock library. SETNX is atomic and sufficient.

3. **Rejections are permanent per ride.** Once a driver rejects a ride, they must NOT
   receive another offer for the same ride, even if the fare is boosted. Always check
   `driver_rejected:{rideId}` before offering.

4. **Soft rejects (timeouts) are NOT rejections.** If a driver's 15-second offer window
   expires without action, do NOT increment the rejection counter, do NOT publish
   `DriverRejectedRideEvent`. The rider's rejection counter only shows explicit rejections.

5. **Fire-and-forget broadcasting.** The broadcast phase publishes Kafka events and returns
   immediately. The matching-service does NOT block waiting for driver responses.
   Responses arrive asynchronously via the accept/reject endpoints.

6. **Never call mapping APIs directly.** All geo queries (nearby drivers, geofence checks,
   distance calculations) go through location-service via RestClient.

7. **Money uses BigDecimal only.** Fare amounts in `DriverOfferNotificationEvent` and all
   DTOs use `BigDecimal`. Never `double` or `float` for money.

8. **Driver keeps 100% of fare.** The matching-service does not deduct any percentage.
   The boosted fare goes entirely to the driver.

9. **All Redis keys have TTLs.** No Redis key should live forever. Ride-related keys
   expire in 300s. Dedup keys expire in 20s. Acceptance locks expire in 60s.
   `driver_stats` hashes are the exception -- they persist until manually cleaned.

10. **Clean up on cancellation.** When a ride is cancelled, ALL Redis keys for that ride
    must be deleted. Stale keys can cause phantom offers.

---

## 18. Testing Strategy

### Unit Tests
- `DriverScoringService.scoreCandidate()` -- all weight combinations, edge cases
- `AcceptanceService.tryAcceptRide()` -- race condition simulation
- `filterNewCandidates()` -- proper exclusion of offered and rejected drivers
- `ExpansionScheduler` -- batch progression, timeout handling
- Service area validation -- accepted, rejected (no OPERATING zone), rejected (RESTRICTED zone)

### Integration Tests (Testcontainers)
```java
@SpringBootTest
@Testcontainers
class MatchingFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void givenRideRequested_whenMultipleDriversAccept_thenOnlyFirstWins() { ... }

    @Test
    void givenDriverRejected_whenFareBoosted_thenRejectedDriverNotReOffered() { ... }

    @Test
    void givenAllBatchesExhausted_whenNoAcceptance_thenNoDriverFoundEventPublished() { ... }
}
```

### Test Naming Convention
```java
@Test
void givenThreeNearbyDrivers_whenRideRequested_thenAllThreeReceiveOffer() { ... }

@Test
void givenDriverAlreadyOffered_whenSameRideBroadcast_thenDriverSkipped() { ... }

@Test
void givenRideCancelled_whenCleanupRuns_thenAllRedisKeysDeleted() { ... }
```

---

## Charter, Cargo & Flat Fee Expansion (Phase 7-9)

### Rides: No Change (Phase 7)

- On-demand ride matching continues to use the existing broadcast-and-accept model
- No changes to the broadcast, scoring, accept race, or expansion scheduler for `serviceCategory=RIDE`

### Charter & Cargo: Marketplace Model (Phase 8)

- Charter and cargo bookings use a **marketplace model**, not broadcast-and-accept
- Bookings are listed in a marketplace feed that drivers can browse
- Drivers filter by: `serviceCategory`, `vehicleType`, `qualityTier`, `scheduledPickupAt` range
- Driver sends an acceptance request (not an instant accept) -- customer confirms which driver to use
- No broadcast push notifications for scheduled bookings -- drivers proactively browse
- Redis keys for marketplace: `marketplace:{countryCode}:{serviceCategory}:{vehicleType}` (sorted set by scheduledPickupAt)

### Matching Filters (Phase 8)

- New filter dimensions: `serviceCategory`, `qualityTier`, `scheduledPickupAt` range (e.g. "bookings departing in next 48 hours")
- Driver eligibility: must have matching `serviceCategory` in their registered categories, correct vehicle type, and correct quality tier (for charter)

### New Endpoints (Phase 8)

- `GET /api/v1/marketplace/bookings` -- drivers browse available charter/cargo bookings (filtered by vehicle type, category, date range)
- `POST /api/v1/marketplace/bookings/{id}/request` -- driver requests to accept a booking
- `POST /api/v1/marketplace/bookings/{id}/confirm/{driverId}` -- customer confirms a driver (internal, called by ride-service)

---

## Implementation Steps

Complete these in order. Each step should compile and pass existing tests before moving on.

- [ ] **1. application.yml** -- Configure port 8086, datasource `twende_matching`, Redis connection, Kafka bootstrap servers and consumer/producer settings, inter-service URLs (`location-service`, `driver-service`, `rating-service`, `ride-service`), matching tuning properties (radii, weights, windows)
- [ ] **2. Entities: OfferLog, DriverStatsSnapshot** -- Create JPA entities extending `BaseEntity`. `OfferLog` with fields: rideId, driverId, countryCode, batchNumber, distanceKm, score, offeredAt, respondedAt, response. `DriverStatsSnapshot` with fields: driverId (PK), offeredCount, acceptedCount, rejectionCount, acceptanceRate, snapshotAt. Create Flyway migration `V1__create_matching_schema.sql`
- [ ] **3. Repositories** -- `OfferLogRepository` (queries by rideId, driverId, offeredAt range). `DriverStatsSnapshotRepository` (findByDriverId, upsert for daily snapshot)
- [ ] **4. Inter-service clients (RestClient)** -- `LocationServiceClient`: geofence check (`isInServiceArea`, `isInRestrictedZone`, `resolveCityId`), nearby drivers (`findNearbyDrivers`). `DriverServiceClient`: driver status and vehicle info. `RatingServiceClient`: driver average rating. `RideServiceClient`: fetch rides in REQUESTED status for expansion scheduler. Configure all in `RestClientConfig` with base URLs from `twende.services.*`
- [ ] **5. DriverScoringService** -- Implement `scoreCandidate()`: distance (50%), rating (30%), acceptance rate (20%). Read acceptance rate from Redis hash `driver_stats:{driverId}`. Normalise distance score as `1 - (distanceKm / radiusKm)`. Normalise rating as `(rating - 1) / 4`. Deprioritize (but do not block) drivers with acceptance rate below 30%
- [ ] **6. MatchingService** -- `onRideRequested()`: validate service area via `LocationServiceClient` (OPERATING zone check, RESTRICTED zone check, resolve cityId). Find candidates via `LocationServiceClient.findNearbyDrivers()` with 3km radius and limit 15. Score candidates via `DriverScoringService`. Take top 10 and delegate to `OfferBroadcastService`
- [ ] **7. OfferBroadcastService** -- `broadcastOffers()`: for each candidate, SETNX `driver_offered:{driverId}:{rideId}` (TTL 20s) for dedup. On success: SADD to `rides_offered_to:{rideId}` (TTL 300s), HINCRBY `driver_stats:{driverId} offered_count`, publish `DriverOfferNotificationEvent` to Kafka topic `twende.drivers.offer-notification`, save `OfferLog` record to DB
- [ ] **8. AcceptanceService** -- `tryAcceptRide()`: SETNX `ride_accepted:{rideId}` = driverId (TTL 60s). If won: publish `RideOfferAcceptedEvent` to `twende.rides.offer-accepted`, update `OfferLog` response to ACCEPTED, HINCRBY accepted_count, recompute acceptance_rate. If lost: return "Ride already accepted", update `OfferLog` response to RIDE_TAKEN. `handleDriverReject()`: SADD `driver_rejected:{rideId}` (TTL 300s), HINCRBY rejection_count, recompute acceptance_rate, update `OfferLog` response to REJECTED, publish `DriverRejectedRideEvent` to `twende.drivers.rejected-ride`
- [ ] **9. ExpansionScheduler** -- `@Scheduled(fixedDelay = 30_000)`: query ride-service for REQUESTED rides. For each ride, read `ride_offer_batches:{rideId}` for current batch. Batch 1 (0-60s): 3km, already done. Batch 2 (61-120s): 5km, filter new candidates NOT in `rides_offered_to` or `driver_rejected` sets, score and broadcast top 10. Batch 3 (121-180s): 10km, same filter logic. After 180s: publish `NoDriverFoundEvent` to `twende.rides.no-driver-found`, cleanup all Redis keys. INCR batch counter after each expansion
- [ ] **10. Re-broadcast on fare boost** -- Kafka consumer for `twende.rides.fare-boosted`: find nearby ONLINE_AVAILABLE drivers, exclude already-offered (SISMEMBER `rides_offered_to:{rideId}`), exclude rejected (SMEMBERS `driver_rejected:{rideId}`), score new candidates and broadcast with updated fare amount
- [ ] **11. Kafka consumers** -- `RideRequestedConsumer` (topic `twende.rides.requested`): delegates to `MatchingService.onRideRequested()`. `RideFareBoostedConsumer` (topic `twende.rides.fare-boosted`): delegates to re-broadcast logic. `RideCancelledConsumer` (topic `twende.rides.cancelled`): delete all Redis keys (`ride_accepted`, `rides_offered_to`, `driver_rejected`, `ride_offer_batches`, per-driver dedup keys), notify offered drivers. `DriverStatusConsumer` (topic `twende.drivers.status-updated`): update local driver availability knowledge
- [ ] **12. Kafka producers (KafkaEventPublisher)** -- Publish to: `twende.rides.offer-accepted` (RideOfferAcceptedEvent), `twende.drivers.rejected-ride` (DriverRejectedRideEvent), `twende.drivers.offer-notification` (DriverOfferNotificationEvent), `twende.rides.no-driver-found` (NoDriverFoundEvent). Use `KafkaTemplate<String, Object>` with rideId as key for partition ordering
- [ ] **13. Driver action endpoints** -- `DriverActionController`: `PUT /internal/driver-actions/{rideId}/accept` (AcceptRequest with driverId) delegates to `AcceptanceService.tryAcceptRide()`. `PUT /internal/driver-actions/{rideId}/reject` (RejectRequest with driverId) delegates to `AcceptanceService.handleDriverReject()`. Both return `ApiResponse`
- [ ] **14. Unit tests + integration tests** -- Unit tests: `DriverScoringService.scoreCandidate()` (all weight combinations, edge cases, low acceptance deprioritization), `AcceptanceService.tryAcceptRide()` (race condition simulation with concurrent calls), `filterNewCandidates()` (proper exclusion of offered/rejected), `ExpansionScheduler` (batch progression, timeout at 180s), service area validation (accepted, rejected for no OPERATING zone, rejected for RESTRICTED zone). Integration tests with Testcontainers (Postgres + Redis + Kafka): full matching flow, duplicate offer prevention, cancellation cleanup, fare boost re-broadcast
- [ ] **15. Dockerfile** — Multi-stage build (eclipse-temurin:21-jdk-alpine for build, 21-jre-alpine for run). Non-root `twende` user. Health check on `/actuator/health`. Expose port 8086.
- [ ] **16. OpenAPI config** — `OpenApiConfig.java` with SpringDoc `OpenAPI` bean. Title: "Matching Service API". Swagger UI at `/swagger-ui.html`.
- [ ] **17. Verify** -- Run `./mvnw -pl matching-service clean verify`, confirm all tests pass and JaCoCo coverage meets 80% threshold
