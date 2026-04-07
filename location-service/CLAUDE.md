# CLAUDE.md — Location Service

> Real-time driver location tracking, geo-queries, geocoding, routing, autocomplete,
> geofencing, and trip trace storage. This is the largest and most complex service in the
> Twende platform. Read this file fully before writing any code.

---

## 1. Service Identity

| Property | Value |
|---|---|
| **Port** | 8087 |
| **Database** | `twende_locations` |
| **Artifact** | `location-service` |
| **Base package** | `tz.co.twende.location` |
| **Scan packages** | `tz.co.twende.location`, `tz.co.twende.common` |

---

## 2. Responsibilities

1. **WebSocket location tracking** — drivers send GPS updates every 3 seconds over a
   persistent WebSocket connection. Updates are written to Redis for live queries and
   buffered for trip trace storage.
2. **Geocoding and reverse geocoding** — address-to-coordinates and coordinates-to-address
   via provider abstraction (Google Maps, Nominatim).
3. **Routing and ETA** — route calculation and estimated time of arrival via provider
   abstraction (Google Maps, OSRM).
4. **Place autocomplete** — search-as-you-type place suggestions via provider abstraction.
5. **Geofencing** — PostGIS-backed zone management. Point-in-polygon checks for service
   area validation, surge zones, airport surcharges, and restricted areas.
6. **Trip trace persistence** — during active rides, location points are buffered in Redis.
   On ride completion, the trace is flushed to PostgreSQL for compliance and replay.
7. **Rider location push** — during an active ride, the server relays driver location
   updates to the rider's WebSocket session in real time.

---

## 3. WebSocket Endpoint

```
ws://host/ws/location?token={jwt}
```

The JWT access token is validated on the WebSocket handshake in a `HandshakeInterceptor`.
If the token is invalid or expired, the handshake is rejected with HTTP 401. The validated
user identity (driver ID, role, country code) is cached in the WebSocket session attributes
for the duration of the connection.

### Driver -> Server (location update)

```json
{
  "type": "LOCATION_UPDATE",
  "latitude": -6.7924,
  "longitude": 39.2083,
  "bearing": 45,
  "speedKmh": 32,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

### Server -> Rider (driver location during active ride)

```json
{
  "type": "DRIVER_LOCATION",
  "rideId": "uuid",
  "latitude": -6.7924,
  "longitude": 39.2083,
  "bearing": 45,
  "estimatedArrivalSeconds": 180
}
```

### Heartbeat

Clients send `PING` every 30 seconds. Server responds with `PONG`. Connections silent for
more than 90 seconds are considered stale and closed by the server.

### Session Registry

`WebSocketSessionRegistry` maintains a `ConcurrentHashMap<UUID, WebSocketSession>` mapping
user IDs to their active WebSocket sessions. This is used to push driver location updates
to riders during active rides.

### Multi-instance Scalability

In multi-instance deployment, WebSocket sessions are partitioned. When a driver location
update arrives on instance A but the rider is connected to instance B, instance A publishes
to Redis channel `ws:rider:{riderId}` and instance B relays it to the rider's session.

---

## 4. Location Update Flow

```
Driver App -> WebSocket -> location-service
  1. Validate JWT on handshake (cached for session duration)
  2. On LOCATION_UPDATE message:
     a. Update Redis GEO set:  GEOADD drivers:{countryCode}:{vehicleType} lng lat driverId
     b. Update Redis Hash:     HSET driver:location:{driverId} lat lng bearing speed updatedAt
     c. Set TTL on hash:       EXPIRE driver:location:{driverId} 90
     d. If driver has active ride:  RPUSH ride:trace:{rideId} {lat,lng,ts}
     e. If rider is connected:      push DRIVER_LOCATION to rider's WebSocket session
  3. On disconnect:
     a. Mark driver status as OFFLINE in Redis
     b. Remove from GEO set if no active ride
```

### ETA Calculation

On `DRIVER_ASSIGNED`, the service begins computing ETA using the configured routing provider
(e.g., Google Maps Distance Matrix) every 30 seconds and pushes updates to the rider's
WebSocket session.

---

## 5. Redis Keys

| Key | Type | TTL | Description |
|---|---|---|---|
| `drivers:{countryCode}:{vehicleType}` | GEO sorted set | none | Live driver positions. Updated on each location message. Removed on offline/ride complete. |
| `driver:location:{driverId}` | Hash | 90s | Last known position: lat, lng, bearing, speed, updatedAt. Stale if no updates within 90s. |
| `ride:trace:{rideId}` | List | 48h | Ordered list of `{lat,lng,ts}` points during active ride (`IN_PROGRESS` status). |

### Redis GEO Operations

```java
// Add/update driver position
redisTemplate.opsForGeo().add(
    "drivers:" + countryCode + ":" + vehicleType,
    new Point(lng, lat), driverId.toString()
);

// Set driver detail hash (TTL 90s - stale if no updates)
redisTemplate.opsForHash().putAll("driver:location:" + driverId, Map.of(
    "lat", String.valueOf(lat),
    "lng", String.valueOf(lng),
    "bearing", String.valueOf(bearing),
    "speed", String.valueOf(speed),
    "updatedAt", Instant.now().toString()
));
redisTemplate.expire("driver:location:" + driverId, 90, TimeUnit.SECONDS);

// Find nearby drivers - NO COUNT limit, broadcast to ALL nearby
List<GeoResult<RedisGeoCommands.GeoLocation<String>>> nearby =
    redisTemplate.opsForGeo().radius(
        "drivers:" + countryCode + ":" + vehicleType,
        new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS)),
        GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().sortAscending()
    );
```

### Trip Trace

During `IN_PROGRESS` rides, each driver location update is appended to the trace:

```java
redisTemplate.opsForList().rightPush(
    "ride:trace:" + rideId,
    objectMapper.writeValueAsString(Map.of("lat", lat, "lng", lng, "ts", timestamp))
);
redisTemplate.expire("ride:trace:" + rideId, 48, TimeUnit.HOURS);
```

On ride completion (Kafka event), the trace is flushed from Redis to the `trip_traces`
PostgreSQL table and removed from Redis.

---

## 6. Provider Abstraction (CRITICAL)

The location service uses a provider abstraction layer that allows per-city switching
between mapping providers. This is the same pattern used by payment and notification
services. **No module calls mapping APIs directly** -- everything goes through provider
interfaces resolved by `ProviderFactory`.

### Provider Interfaces

```java
public interface GeocodingProvider {
    String getId();  // "google", "nominatim"
    GeocodingResult geocode(String address, LatLng bias);
    GeocodingResult reverseGeocode(LatLng point);
}

public interface RoutingProvider {
    String getId();  // "google", "osrm"
    Route getRoute(LatLng origin, LatLng destination);
    int getEtaMinutes(LatLng origin, LatLng destination);
}

public interface AutocompleteProvider {
    String getId();  // "google", "nominatim"
    List<PlaceResult> search(String query, LatLng bias, String countryCode, int limit);
}
```

### ProviderFactory

Resolves the correct provider implementation per city based on the `OperatingCity` config
columns (`geocoding_provider`, `routing_provider`, `autocomplete_provider`). Provider
switching is **per-city, not global** -- changing a provider for one city does not affect
other cities.

```java
@Component
public class ProviderFactory {
    private final Map<String, GeocodingProvider> geocodingProviders;
    private final Map<String, RoutingProvider> routingProviders;
    private final Map<String, AutocompleteProvider> autocompleteProviders;
    private final CountryConfigClient countryConfigClient;  // REST call to country-config-service

    public GeocodingProvider geocodingFor(UUID cityId) {
        OperatingCityDto city = countryConfigClient.getCity(cityId);
        return geocodingProviders.get(city.getGeocodingProvider().toLowerCase());
    }

    public RoutingProvider routingFor(UUID cityId) {
        OperatingCityDto city = countryConfigClient.getCity(cityId);
        return routingProviders.get(city.getRoutingProvider().toLowerCase());
    }

    public AutocompleteProvider autocompleteFor(UUID cityId) {
        OperatingCityDto city = countryConfigClient.getCity(cityId);
        return autocompleteProviders.get(city.getAutocompleteProvider().toLowerCase());
    }
}
```

### GoogleMapsClient (RestClient, NO SDK)

All Google Maps API calls use Spring `RestClient` directly. The `google-maps-services` SDK
must **never** be in `pom.xml`. The client wraps the Google Maps REST API endpoints:

```java
@Component
public class GoogleMapsClient {
    private final RestClient restClient;
    private final String apiKey;

    public GoogleMapsClient(@Value("${twende.maps.google.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
            .baseUrl("https://maps.googleapis.com/maps/api")
            .defaultHeader("Accept", "application/json")
            .build();
    }

    public GeocodingResponse geocode(String address) { ... }
    public GeocodingResponse reverseGeocode(double lat, double lng) { ... }
    public DirectionsResponse directions(double oLat, double oLng, double dLat, double dLng) { ... }
    public DistanceMatrixResponse distanceMatrix(double oLat, double oLng, double dLat, double dLng) { ... }
    public PlacesAutocompleteResponse autocomplete(String input, double lat, double lng, String countryCode) { ... }
}
```

### Google Provider Implementations

- `GoogleGeocodingProvider` -- implements `GeocodingProvider`, delegates to `GoogleMapsClient`
- `GoogleRoutingProvider` -- implements `RoutingProvider`, delegates to `GoogleMapsClient`
- `GoogleAutocompleteProvider` -- implements `AutocompleteProvider`, delegates to `GoogleMapsClient`

### OSRM and Nominatim Stubs

- `OsrmRoutingProvider` -- implements `RoutingProvider`, stub for Phase 2. Uses `OsrmClient`
  (RestClient wrapper for self-hosted OSRM instance).
- `NominatimGeocodingProvider` -- implements `GeocodingProvider`, stub for Phase 3. Uses
  `NominatimClient` (RestClient wrapper for self-hosted Nominatim).

These are interface-implemented but not wired as active providers until the corresponding
phase. The `ProviderFactory` will automatically pick them up when an `OperatingCity` has
its provider columns set to `OSRM` or `NOMINATIM`.

### Provider Migration Plan

| Phase | Change | Impact |
|---|---|---|
| Phase 1 (current) | All cities use `GOOGLE` for geocoding, routing, and autocomplete | Baseline |
| Phase 2 | Migrate routing to self-hosted OSRM per city | Update `routing_provider` column in `operating_cities` |
| Phase 3 | Migrate geocoding to Nominatim per city | Update `geocoding_provider` column. Evaluate autocomplete alternatives. |

**Zero code changes when switching** -- `ProviderFactory` reads per-city config at runtime.
Update the database column and the next request uses the new provider.

---

## 7. PostGIS Zones and Geofencing

### Zone Entity

```java
@Entity
@Table(name = "zones")
@Getter @Setter @NoArgsConstructor
public class Zone extends BaseEntity {
    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false)
    private UUID cityId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "GEOGRAPHY(POLYGON, 4326)", nullable = false)
    private Geometry boundary;  // org.locationtech.jts.geom.Geometry

    @Column(nullable = false, length = 20)
    private String type;  // OPERATING, SURGE, AIRPORT, RESTRICTED, PICKUP_ONLY

    @Column(columnDefinition = "JSONB DEFAULT '{}'")
    private String config;  // JSON string

    @Column(nullable = false)
    private boolean isActive = true;
}
```

### Zone Types

| Type | Purpose | Config example |
|---|---|---|
| `OPERATING` | Service boundary -- rides can only start within this zone | `{}` |
| `SURGE` | Surge pricing area with zone-level multiplier | `{"multiplier": 1.5}` |
| `AIRPORT` | Surcharge + pickup instructions for airport locations | `{"surcharge": 2000, "pickupInstructions": "Terminal 2 parking"}` |
| `RESTRICTED` | No service allowed -- ride requests rejected | `{"reason": "Government restricted area"}` |
| `PICKUP_ONLY` | Pickup allowed, no dropoff | `{}` |

### ZoneRepository (Native Queries with ST_Covers)

**All point-in-polygon checks use PostGIS `ST_Covers` via native queries. Never iterate
polygons in Java code.**

```java
public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    @Query(value = """
        SELECT * FROM zones
        WHERE city_id = :cityId AND type = :type AND is_active = true
        AND ST_Covers(boundary, ST_Point(:lng, :lat)::geography)
        LIMIT 1
        """, nativeQuery = true)
    Optional<Zone> findActiveZoneContainingPoint(
        @Param("cityId") UUID cityId,
        @Param("type") String type,
        @Param("lng") BigDecimal lng,
        @Param("lat") BigDecimal lat
    );

    @Query(value = """
        SELECT * FROM zones
        WHERE city_id = :cityId AND is_active = true
        AND ST_Covers(boundary, ST_Point(:lng, :lat)::geography)
        """, nativeQuery = true)
    List<Zone> findAllActiveZonesContainingPoint(
        @Param("cityId") UUID cityId,
        @Param("lng") BigDecimal lng,
        @Param("lat") BigDecimal lat
    );
}
```

### GeofenceService

```java
@Service
public class GeofenceService {
    private final ZoneRepository zoneRepository;

    public Optional<Zone> findZone(UUID cityId, String type, BigDecimal lat, BigDecimal lng) {
        return zoneRepository.findActiveZoneContainingPoint(cityId, type, lng, lat);
    }

    public boolean isInServiceArea(UUID cityId, BigDecimal lat, BigDecimal lng) {
        return findZone(cityId, "OPERATING", lat, lng).isPresent();
    }

    public List<Zone> findAllZonesContaining(UUID cityId, BigDecimal lat, BigDecimal lng) {
        return zoneRepository.findAllActiveZonesContainingPoint(cityId, lng, lat);
    }
}
```

**Important:** Note the parameter order -- PostGIS `ST_Point` takes `(lng, lat)`, not
`(lat, lng)`. The service methods accept `(lat, lng)` to match the natural API convention
and swap them when calling the repository.

---

## 8. Geocode Caching

### GeocodeCache Entity (Database, not Redis)

```java
@Entity
@Table(name = "geocode_cache")
@Getter @Setter @NoArgsConstructor
public class GeocodeCache extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String queryHash;  // SHA-256 of normalized query

    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 30)
    private String provider;

    @Column(nullable = false)
    private int hitCount = 1;

    private Instant expiresAt;  // 30-day TTL from creation
}
```

### GeocodingService (Cache-Through Pattern)

```java
@Service
public class GeocodingService {
    private final GeocodeCacheRepository geocodeCacheRepository;
    private final ProviderFactory providerFactory;

    public GeocodingResult geocode(String address, LatLng bias, UUID cityId) {
        String hash = DigestUtils.sha256Hex(address.toLowerCase().strip());
        Optional<GeocodeCache> cached = geocodeCacheRepository.findByQueryHash(hash);
        if (cached.isPresent() && cached.get().getExpiresAt().isAfter(Instant.now())) {
            cached.get().setHitCount(cached.get().getHitCount() + 1);
            geocodeCacheRepository.save(cached.get());
            return toResult(cached.get());
        }
        GeocodingProvider provider = providerFactory.geocodingFor(cityId);
        GeocodingResult result = provider.geocode(address, bias);
        saveToCache(hash, address, result, provider.getId());
        return result;
    }
    // Cache entries expire after 30 days. Scheduled cleanup weekly.
}
```

### Caching Strategy Summary

| Data | Cache location | TTL | Key strategy |
|---|---|---|---|
| Geocoding / reverse geocoding | PostgreSQL `geocode_cache` table | 30 days | SHA-256 of normalized query |
| Distance matrix / directions | Redis via `@Cacheable` | 1 hour | Coordinates rounded to 3 decimal places |
| Autocomplete | NOT cached | -- | Session-dependent, low cache hit rate |

---

## 9. Database Schema (Flyway Migrations)

### V1__create_locations_schema.sql

```sql
-- Enable PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

-- Completed trip GPS traces (for compliance and replay)
CREATE TABLE trip_traces (
    id            UUID         PRIMARY KEY,
    ride_id       UUID         NOT NULL UNIQUE,
    country_code  CHAR(2)      NOT NULL,
    driver_id     UUID         NOT NULL,
    trace         JSONB        NOT NULL,  -- array of {lat, lng, ts} points
    distance_metres INT,
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_traces_ride ON trip_traces(ride_id);
CREATE INDEX idx_trip_traces_driver ON trip_traces(driver_id);

-- Geofence zones
CREATE TABLE zones (
    id            UUID         PRIMARY KEY,
    country_code  CHAR(2)      NOT NULL,
    city_id       UUID         NOT NULL,
    name          VARCHAR(100) NOT NULL,
    boundary      GEOGRAPHY(POLYGON, 4326) NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    config        JSONB        DEFAULT '{}',
    is_active     BOOLEAN      DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_zones_city_type ON zones(city_id, type);
CREATE INDEX idx_zones_boundary ON zones USING GIST(boundary);

-- Geocode cache
CREATE TABLE geocode_cache (
    id            UUID         PRIMARY KEY,
    country_code  CHAR(2)      NOT NULL,
    query_hash    VARCHAR(64)  UNIQUE NOT NULL,
    query         TEXT         NOT NULL,
    latitude      NUMERIC(10,7) NOT NULL,
    longitude     NUMERIC(10,7) NOT NULL,
    address       TEXT,
    provider      VARCHAR(30),
    hit_count     INTEGER      DEFAULT 1,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ
);

CREATE INDEX idx_geocache_hash ON geocode_cache(query_hash);
```

### Important Schema Notes

- **PostGIS extension required** -- `CREATE EXTENSION IF NOT EXISTS postgis` must be first.
- **Zone boundary** uses `GEOGRAPHY(POLYGON, 4326)` for accurate distance calculations on
  the Earth's surface.
- **GIST index** on `zones.boundary` is essential for performant spatial queries.
- **trip_traces.trace** is JSONB containing an array of `{lat, lng, ts}` objects.
- **All IDs** are ULIDs generated by the application (via `UlidGenerator`), stored as UUID.

---

## 10. Kafka Integration

### Consumed Topics

| Topic | Event | Action |
|---|---|---|
| `twende.rides.completed` | `RideCompletedEvent` | Flush `ride:trace:{rideId}` from Redis to `trip_traces` table. Calculate total distance from trace. Remove driver from GEO set. |
| `twende.drivers.status-updated` | `DriverStatusUpdatedEvent` | If status is `OFFLINE`, remove driver from GEO set. If `ONLINE_AVAILABLE`, add to GEO set. |

### Kafka Consumer Implementation

```java
@Component
public class LocationKafkaConsumer {

    @KafkaListener(topics = "twende.rides.completed", groupId = "location-service")
    public void onRideCompleted(RideCompletedEvent event) {
        // 1. Read trace from Redis: LRANGE ride:trace:{rideId} 0 -1
        // 2. Parse points, calculate total distance
        // 3. Save to trip_traces table
        // 4. Delete Redis key: DEL ride:trace:{rideId}
        // 5. Remove driver from GEO set
    }

    @KafkaListener(topics = "twende.drivers.status-updated", groupId = "location-service")
    public void onDriverStatusUpdated(DriverStatusUpdatedEvent event) {
        if (event.getNewStatus() == DriverStatus.OFFLINE) {
            // Remove from GEO set: ZREM drivers:{cc}:{vehicleType} driverId
        }
    }
}
```

---

## 11. API Endpoints

### Public Geocoding and Routing

```
GET    /api/v1/locations/geocode?address=&cityId=           Address -> lat/lng
GET    /api/v1/locations/reverse?lat=&lng=&cityId=          Lat/lng -> address
GET    /api/v1/locations/autocomplete?q=&lat=&lng=&cityId=  Place search
POST   /api/v1/locations/route                               Route between two points
POST   /api/v1/locations/eta                                 ETA only
```

### Zone Queries

```
GET    /api/v1/locations/zones/check?lat=&lng=&cityId=      What zones contain this point?
GET    /api/v1/locations/cities/{cityId}/zones               List zones for city
POST   /api/v1/locations/cities/{cityId}/zones               Create zone (ADMIN)
PUT    /api/v1/locations/zones/{id}                          Update zone (ADMIN)
DELETE /api/v1/locations/zones/{id}                          Deactivate zone (ADMIN)
```

### Internal Endpoints (called by other services)

```
GET    /internal/location/drivers/nearby                     Nearby available drivers (used by matching-service)
GET    /internal/location/driver/{driverId}                  Last known location
GET    /internal/location/rides/{rideId}/trace               Trip GPS trace (used by compliance-service)
```

### Response Format

All endpoints return `ApiResponse<T>` wrapper from `common-lib`:

```java
@GetMapping("/geocode")
public ResponseEntity<ApiResponse<GeocodingResult>> geocode(
        @RequestParam String address,
        @RequestParam UUID cityId) {
    return ResponseEntity.ok(ApiResponse.ok(geocodingService.geocode(address, null, cityId)));
}
```

---

## 12. Package Structure

```
tz.co.twende.location
├── LocationServiceApplication.java
├── config/
│   ├── WebSocketConfig.java             # WebSocket endpoint registration
│   ├── RedisConfig.java                 # RedisTemplate beans
│   ├── SecurityConfig.java              # Resource server JWT validation
│   ├── GoogleMapsProperties.java        # @ConfigurationProperties for twende.maps.google
│   ├── OsrmProperties.java             # @ConfigurationProperties for twende.maps.osrm
│   └── NominatimProperties.java         # @ConfigurationProperties for twende.maps.nominatim
├── entity/
│   ├── Zone.java
│   ├── GeocodeCache.java
│   └── TripTrace.java
├── repository/
│   ├── ZoneRepository.java              # Native queries with ST_Covers
│   ├── GeocodeCacheRepository.java
│   └── TripTraceRepository.java
├── service/
│   ├── LocationService.java             # Redis GEO operations, nearby driver queries
│   ├── GeocodingService.java            # Cache-through geocoding
│   ├── RoutingService.java              # Route + ETA delegation to provider
│   ├── AutocompleteService.java         # Autocomplete delegation to provider
│   ├── GeofenceService.java             # PostGIS point-in-polygon zone checks
│   ├── ZoneService.java                 # Zone CRUD
│   └── TripTraceService.java            # Flush trace from Redis to DB
├── provider/
│   ├── GeocodingProvider.java           # Interface
│   ├── RoutingProvider.java             # Interface
│   ├── AutocompleteProvider.java        # Interface
│   ├── ProviderFactory.java             # Resolves provider per city config
│   ├── google/
│   │   ├── GoogleMapsClient.java        # RestClient for Google Maps REST APIs
│   │   ├── GoogleGeocodingProvider.java
│   │   ├── GoogleRoutingProvider.java
│   │   └── GoogleAutocompleteProvider.java
│   ├── osrm/
│   │   ├── OsrmClient.java             # RestClient for OSRM (stub -- Phase 2)
│   │   └── OsrmRoutingProvider.java     # Stub -- Phase 2
│   └── nominatim/
│       ├── NominatimClient.java         # RestClient for Nominatim (stub -- Phase 3)
│       └── NominatimGeocodingProvider.java  # Stub -- Phase 3
├── controller/
│   ├── GeocodingController.java
│   ├── RoutingController.java
│   ├── ZoneController.java
│   └── InternalLocationController.java  # /internal/* endpoints
├── websocket/
│   ├── LocationWebSocketHandler.java    # Handles LOCATION_UPDATE messages
│   ├── WebSocketSessionRegistry.java    # ConcurrentHashMap<UUID, WebSocketSession>
│   └── JwtHandshakeInterceptor.java     # Validates JWT on WS handshake
├── kafka/
│   └── LocationKafkaConsumer.java       # Consumes ride.completed, driver.status-updated
├── dto/
│   ├── GeocodingResult.java
│   ├── Route.java
│   ├── PlaceResult.java
│   ├── LatLng.java
│   ├── LocationUpdateMessage.java
│   ├── DriverLocationMessage.java
│   ├── NearbyDriverRequest.java
│   ├── NearbyDriverResponse.java
│   ├── ZoneCheckResponse.java
│   ├── CreateZoneRequest.java
│   └── UpdateZoneRequest.java
└── mapper/
    └── ZoneMapper.java                  # MapStruct mapper
```

---

## 13. Configuration Properties

```yaml
server:
  port: 8087

spring:
  application:
    name: location-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_locations
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
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
      group-id: location-service
      auto-offset-reset: earliest

twende:
  maps:
    google:
      api-key: ${GOOGLE_MAPS_API_KEY:}
      enabled: ${GOOGLE_MAPS_ENABLED:true}
    osrm:
      base-url: ${OSRM_BASE_URL:http://localhost:5000}
      enabled: ${OSRM_ENABLED:false}
    nominatim:
      base-url: ${NOMINATIM_BASE_URL:http://localhost:8088}
      enabled: ${NOMINATIM_ENABLED:false}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

---

## 14. Critical Rules

1. **No Google Maps SDK** -- the `google-maps-services` dependency must never be in
   `pom.xml`. All Google Maps API calls go through `GoogleMapsClient` using Spring
   `RestClient` directly.

2. **Google Maps API key is never exposed to clients** -- all mapping API calls are
   server-side. Frontend gets results from our endpoints only.

3. **Provider switching is per-city, not global** -- each `OperatingCity` has its own
   `geocoding_provider`, `routing_provider`, and `autocomplete_provider` columns.
   Changing a provider for one city must not affect other cities.

4. **Zone checks use PostGIS, not application code** -- all point-in-polygon checks must
   use `ST_Covers` via native queries in `ZoneRepository`. Never iterate polygons in Java.

5. **ST_Point takes (lng, lat)** -- PostGIS uses longitude-first ordering. The service
   methods accept `(lat, lng)` and swap them when calling native queries.

6. **Rides cannot start in RESTRICTED zones** -- if the pickup point falls inside a zone
   with type `RESTRICTED`, the ride request must be rejected. This check is performed by
   the caller (ride-service or matching-service) using this service's zone check endpoint.

7. **Autocomplete is NOT cached** -- session-dependent with low cache hit rate.

8. **Distance/direction caching in Redis** -- use `@Cacheable` with coordinates rounded
   to 3 decimal places as cache key. TTL of 1 hour.

9. **Geocode caching in PostgreSQL** -- 30-day TTL. Cache key is SHA-256 hash of the
   normalized (lowercased, stripped) query string.

10. **Trip traces are ephemeral in Redis** -- 48-hour TTL on `ride:trace:{rideId}` keys.
    On ride completion, flush to PostgreSQL immediately. Do not rely on Redis persistence
    for compliance data.

11. **Money uses BigDecimal** -- never `double` or `float`, including for coordinates
    in entities (use `BigDecimal` with `NUMERIC(10,7)` in the database). `Point` objects
    in Redis operations may use `double` as required by the Spring Data Redis API.

12. **All timestamps are Instant (UTC)** -- never `LocalDateTime` or `Date`.

13. **WebSocket JWT validation on handshake only** -- validate the JWT in the
    `HandshakeInterceptor`. Do not re-validate on every message. Cache the user identity
    in session attributes.

---

## 15. Testing

### Unit Tests

- `GeofenceService` -- zone lookup logic
- `GeocodingService` -- cache-through pattern (cache hit, cache miss, expired cache)
- `ProviderFactory` -- correct provider resolution per city config
- `GoogleMapsClient` -- response parsing (mock HTTP responses)
- `LocationWebSocketHandler` -- message parsing and Redis operation calls
- Coordinate rounding for cache keys

### Integration Tests (Testcontainers)

```java
@SpringBootTest
@Testcontainers
class LocationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgis/postgis:16-3.4-alpine");
    // NOTE: Use postgis image, not plain postgres, for PostGIS extension support

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
}
```

**Important:** Use `postgis/postgis` Docker image for integration tests, not plain
`postgres`. The `CREATE EXTENSION IF NOT EXISTS postgis` migration will fail without the
PostGIS extension available in the container.

### Test Naming Convention

```java
@Test
void givenDriverOnline_whenLocationUpdateReceived_thenRedisGeoSetUpdated() { ... }

@Test
void givenPointInRestrictedZone_whenCheckZone_thenZoneReturned() { ... }

@Test
void givenCachedGeocode_whenSameQueryRequested_thenCacheHitAndHitCountIncremented() { ... }

@Test
void givenRideCompleted_whenKafkaEventConsumed_thenTracesFlushedToPostgres() { ... }
```

### Coverage

- Minimum 80% line coverage enforced by JaCoCo
- Run: `./mvnw verify`
- Report: `target/site/jacoco/index.html`
- Excluded from coverage: entities, DTOs, enums, config classes, `LocationServiceApplication`

---

## 16. Inter-Service Dependencies

| Service | Direction | Protocol | Purpose |
|---|---|---|---|
| `country-config-service` | outbound REST | HTTP | Fetch `OperatingCity` config for provider resolution |
| `matching-service` | inbound REST | HTTP | Queries nearby drivers via `/internal/location/drivers/nearby` |
| `compliance-service` | inbound REST | HTTP | Fetches trip traces via `/internal/location/rides/{rideId}/trace` |
| `ride-service` | inbound Kafka | Kafka | `twende.rides.completed` triggers trace flush |
| `driver-service` | inbound Kafka | Kafka | `twende.drivers.status-updated` triggers GEO set update |
| `pricing-service` | inbound REST | HTTP | Calls route/ETA endpoints for fare estimation |

---

## Implementation Steps

Complete these in order. Each step should compile and pass tests before moving to the next.

- [ ] **Step 1: application.yml** — configure port 8087, datasource `twende_locations`, Redis, Kafka (bootstrap servers, consumer group `location-service`, auto-offset-reset earliest), Google Maps API key, OSRM base URL, Nominatim base URL, JPA ddl-auto validate, Flyway, actuator endpoints
- [ ] **Step 2: Config classes** — `GoogleMapsProperties`, `OsrmProperties`, `NominatimProperties` as `@ConfigurationProperties` beans under `twende.maps.google`, `twende.maps.osrm`, `twende.maps.nominatim`. Add `RedisConfig`, `SecurityConfig` (resource server JWT validation), `WebSocketConfig`
- [ ] **Step 3: Entities** — `Zone` (with `GEOGRAPHY(POLYGON, 4326)` column via JTS `Geometry`, countryCode, cityId, name, type, config JSONB, isActive), `GeocodeCache` (queryHash unique, query, latitude/longitude as `BigDecimal(10,7)`, address, provider, hitCount, expiresAt), `TripTrace` (rideId unique, countryCode, driverId, trace JSONB, distanceMetres, startedAt, completedAt). All extend `BaseEntity`
- [ ] **Step 4: Repositories** — `ZoneRepository` with native queries using `ST_Covers`: `findActiveZoneContainingPoint(cityId, type, lng, lat)` and `findAllActiveZonesContainingPoint(cityId, lng, lat)`. `GeocodeCacheRepository` with `findByQueryHash(String hash)`. `TripTraceRepository` with `findByRideId(UUID rideId)`
- [ ] **Step 5: Provider interfaces** — `GeocodingProvider` (getId, geocode, reverseGeocode), `RoutingProvider` (getId, getRoute, getEtaMinutes), `AutocompleteProvider` (getId, search). Define `GeocodingResult`, `Route`, `PlaceResult`, `LatLng` DTOs used by these interfaces
- [ ] **Step 6: GoogleMapsClient** — Spring `RestClient` wrapper with base URL `https://maps.googleapis.com/maps/api`. Methods: `geocode(address)`, `reverseGeocode(lat, lng)`, `directions(oLat, oLng, dLat, dLng)`, `distanceMatrix(oLat, oLng, dLat, dLng)`, `autocomplete(input, lat, lng, countryCode)`. Parse Google-specific JSON responses into internal DTOs. NO Google Maps SDK
- [ ] **Step 7: Google provider implementations** — `GoogleGeocodingProvider` implements `GeocodingProvider`, delegates to `GoogleMapsClient`. `GoogleRoutingProvider` implements `RoutingProvider`, delegates to `GoogleMapsClient`. `GoogleAutocompleteProvider` implements `AutocompleteProvider`, delegates to `GoogleMapsClient`
- [ ] **Step 8: OSRM + Nominatim stubs** — `OsrmRoutingProvider` implements `RoutingProvider` with `getId()` returning `"osrm"`, methods throw `UnsupportedOperationException("OSRM provider not yet implemented")`. `OsrmClient` stub class. `NominatimGeocodingProvider` implements `GeocodingProvider` with `getId()` returning `"nominatim"`, methods throw `UnsupportedOperationException`. `NominatimClient` stub class
- [ ] **Step 9: ProviderFactory** — inject `Map<String, GeocodingProvider>`, `Map<String, RoutingProvider>`, `Map<String, AutocompleteProvider>` (Spring auto-collects by bean name). Create `CountryConfigClient` (RestClient to country-config-service) to fetch `OperatingCity` config. Methods: `geocodingFor(cityId)`, `routingFor(cityId)`, `autocompleteFor(cityId)` — resolve provider string from city config and look up in the map
- [ ] **Step 10: GeocodingService** — cache-through with `geocode_cache` table. On geocode request: compute SHA-256 hash of `address.toLowerCase().strip()`, check DB cache, if hit and not expired increment hitCount and return, if miss delegate to `providerFactory.geocodingFor(cityId)`, save result to cache with 30-day expiresAt. `reverseGeocode` method delegates directly (no cache). Add `@Scheduled` weekly cleanup of expired cache entries
- [ ] **Step 11: RoutingService** — delegate to `providerFactory.routingFor(cityId)`. Cache directions in Redis via `@Cacheable(value = "routes", key = "...")` with coordinates rounded to 3 decimal places, 1-hour TTL. Methods: `getRoute(origin, destination, cityId)`, `getEtaMinutes(origin, destination, cityId)`
- [ ] **Step 12: AutocompleteService** — delegate to `providerFactory.autocompleteFor(cityId)`. NO caching. Method: `search(query, bias, countryCode, cityId, limit)`
- [ ] **Step 13: GeofenceService** — `findZone(cityId, type, lat, lng)` delegates to `ZoneRepository.findActiveZoneContainingPoint` (swap lat/lng to lng/lat for PostGIS). `isInServiceArea(cityId, lat, lng)` checks for OPERATING zone. `findAllZonesContaining(cityId, lat, lng)` returns all active zones containing the point
- [ ] **Step 14: ZoneService** — CRUD for zones. `createZone(cityId, request)` — validate boundary polygon, save. `updateZone(zoneId, request)` — update name, boundary, config, type. `deactivateZone(zoneId)` — set `isActive = false`. `listZones(cityId)` — return all zones for a city. Admin-only operations
- [ ] **Step 15: LocationService** — Redis GEO operations. `addDriverToGeoIndex(driverId, countryCode, vehicleType, lat, lng)` — GEOADD + HSET with 90s TTL. `removeDriverFromGeoIndex(driverId, countryCode, vehicleType)` — ZREM + DEL hash. `findNearbyDrivers(countryCode, vehicleType, lat, lng, radiusKm)` — GEORADIUS sorted ascending by distance, return all (no count limit). `getDriverLocation(driverId)` — read from hash
- [ ] **Step 16: WebSocket** — `LocationWebSocketHandler` extends `TextWebSocketHandler`, parses `LOCATION_UPDATE` JSON messages, calls `LocationService` to update Redis, appends to trip trace if active ride, pushes to rider if connected. `JwtHandshakeInterceptor` validates JWT on upgrade, caches userId/role/countryCode in session attributes, rejects with 401 if invalid. `WebSocketSessionRegistry` with `ConcurrentHashMap<UUID, WebSocketSession>` for rider session lookup. Handle PING/PONG heartbeat, close stale connections after 90s silence
- [ ] **Step 17: Trip trace** — on each location update during `IN_PROGRESS` ride, `RPUSH ride:trace:{rideId} {lat,lng,ts}` with 48h TTL. `TripTraceService.flushTrace(rideId, driverId, countryCode)` — `LRANGE` all points, parse, calculate total distance (Haversine between consecutive points), save to `trip_traces` table, `DEL` Redis key
- [ ] **Step 18: Kafka consumers** — `LocationKafkaConsumer` class. `@KafkaListener(topics = "twende.rides.completed")` — call `TripTraceService.flushTrace()`, remove driver from GEO set. `@KafkaListener(topics = "twende.drivers.status-updated")` — if OFFLINE remove from GEO set, if ONLINE_AVAILABLE add to GEO set (need lat/lng from driver location hash or event payload)
- [ ] **Step 19: Controllers** — `GeocodingController`: GET `/api/v1/locations/geocode`, GET `/api/v1/locations/reverse`. `RoutingController`: POST `/api/v1/locations/route`, POST `/api/v1/locations/eta`. `ZoneController`: GET `/api/v1/locations/zones/check`, GET `/api/v1/locations/cities/{cityId}/zones`, POST (ADMIN), PUT (ADMIN), DELETE (ADMIN). `InternalLocationController`: GET `/internal/location/drivers/nearby`, GET `/internal/location/driver/{driverId}`, GET `/internal/location/rides/{rideId}/trace`. All return `ApiResponse<T>`
- [ ] **Step 20: DTOs + MapStruct mapper** — `LocationUpdateMessage`, `DriverLocationMessage`, `NearbyDriverRequest/Response`, `ZoneCheckResponse`, `CreateZoneRequest`, `UpdateZoneRequest`, `RouteRequest`, `EtaRequest`, `EtaResponse`, `GeocodingResult`, `Route`, `PlaceResult`, `LatLng`. `ZoneMapper` (MapStruct) for entity-to-DTO conversion
- [ ] **Step 21: Unit tests + integration tests** — unit tests for: `GeofenceService` (zone lookup), `GeocodingService` (cache hit/miss/expired), `ProviderFactory` (provider resolution), `GoogleMapsClient` (response parsing with mocked HTTP), `LocationService` (Redis GEO operations), coordinate rounding for cache keys. Integration tests with Testcontainers: `postgis/postgis:16-3.4-alpine` (not plain postgres), `redis:7-alpine`, `confluentinc/cp-kafka:7.5.0`. Test: zone creation + point-in-polygon queries, geocode cache persistence, trip trace flush from Redis to DB, Kafka consumer ride completion flow, WebSocket location update flow. Use Given_When_Then naming
- [ ] **Step 22: Dockerfile** — Multi-stage build (eclipse-temurin:21-jdk-alpine for build, 21-jre-alpine for run). Non-root `twende` user. Health check on `/actuator/health`. Expose port 8087.
- [ ] **Step 23: OpenAPI config** — `OpenApiConfig.java` with SpringDoc `OpenAPI` bean. Title: "Location Service API". Swagger UI at `/swagger-ui.html`.
- [ ] **Step 24: Verify** — run `./mvnw -pl location-service clean verify`, confirm all tests pass with minimum 80% line coverage (JaCoCo), check report at `target/site/jacoco/index.html`
