# CLAUDE.md — Pricing Service

> Fare estimation and final fare calculation for the Twende ride-hailing platform.
> Read this fully before writing any code in this module.

---

## 1. What This Service Does

The pricing service calculates fare estimates (before a ride) and final fares (after a ride)
based on vehicle type pricing rules, distance, duration, surge multipliers, and zone-based
adjustments. It is stateless for fare calculation itself — all pricing rules come from
country-config-service, cached locally in Redis.

**Port:** 8088
**Database:** `twende_pricing`

---

## 2. API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/pricing/estimate` | Fare estimate before ride |
| `POST` | `/api/v1/pricing/calculate` | Final fare after ride (internal, called by ride-service) |
| `GET` | `/api/v1/pricing/surge/{countryCode}/{vehicleType}` | Current surge multiplier |

### Estimate Request / Response

```json
POST /api/v1/pricing/estimate
{
  "countryCode": "TZ",
  "vehicleType": "BAJAJ",
  "pickupLat": -6.7728,
  "pickupLng": 39.2310,
  "dropoffLat": -6.8160,
  "dropoffLng": 39.2803
}

// Response:
{
  "success": true,
  "data": {
    "estimatedFare": 3500,
    "currencyCode": "TZS",
    "displayFare": "TSh 3,500",
    "estimatedDistanceMetres": 8200,
    "estimatedDurationSeconds": 900,
    "surgeMultiplier": 1.0,
    "breakdown": {
      "baseFare": 500,
      "distanceFare": 1640,
      "timeFare": 300,
      "surgeFare": 0,
      "total": 3500
    }
  }
}
```

### Calculate Request (internal)

Called by ride-service after trip completion. Receives actual `distanceMetres` and
`durationSeconds` from the ride record (populated from GPS trip trace).

### Surge Endpoint

Returns the current surge multiplier for a given country and vehicle type. Read from Redis.

---

## 3. Fare Formula

```
fare = max(
  baseFare + (distanceKm * perKm) + (durationMinutes * perMinute),
  minimumFare
) * surgeMultiplier
```

All pricing values (`baseFare`, `perKm`, `perMinute`, `minimumFare`, `surgeMultiplierCap`)
come from `VehicleTypeConfig` retrieved from country-config-service.

### Currency Rounding

Tanzania uses TZS with `minorUnits = 0`. Always round to whole shillings:

```java
fare.setScale(0, RoundingMode.HALF_UP);
```

### Money Rules

- **Always use `BigDecimal`** for all monetary values. Never `double` or `float`.
- DB columns for money: `NUMERIC(12,2)`.

---

## 4. Zone-Based Pricing Adjustments

Before calculating the final fare, check zones containing the pickup and dropoff points
by calling the location-service geofence endpoint. Apply adjustments based on zone type:

| Zone Type | Adjustment |
|---|---|
| `AIRPORT` | Add surcharge from `zone.config.surcharge` to the final fare |
| `SURGE` | Use `zone.config.multiplier` as zone-level surge. Stacks with demand surge but total is capped by `surgeMultiplierCap` |
| `RESTRICTED` | Reject the ride request with an error from `zone.config.reason`. No fare calculated. |

Zone checks use PostGIS `ST_Covers` via the location-service — never iterate polygons in
application code.

---

## 5. Surge Logic

Surge multiplier is calculated per country + vehicle type based on demand/supply ratio:

- **Formula:** `surge = min(activeRequests / availableDrivers, surgeMultiplierCap)`
- **Storage:** Redis key `surge:{countryCode}:{vehicleType}` (float value)
- **Update frequency:** Scheduler runs every 60 seconds
- **Guard:** Surge is only applied if `features.surgeEnabled = true` in the country config
- **Cap:** Never exceeds `surgeMultiplierCap` from `VehicleTypeConfig` (default 2.5x)

Active requests = ride requests in the last 5 minutes. Available drivers = drivers with
`ONLINE_AVAILABLE` status in the area.

---

## 6. Inter-Service Communication

**No Eureka. No Feign. Use Spring `RestClient` for all inter-service calls.**

### Dependencies on Other Services

| Service | What We Get | How |
|---|---|---|
| **country-config-service** | `VehicleTypeConfig` (pricing rules), `CountryConfig` (currency, features) | RestClient, cached in Redis |
| **location-service** | Route distance/duration for estimates, zone checks for pricing adjustments | RestClient |

### For Estimates

Call location-service's routing endpoint to get distance and duration. **Never call Google
Maps or any mapping API directly** — the location-service owns all geo provider interactions.

```java
// Correct: call location-service
Route route = locationServiceClient.getRoute(pickupLat, pickupLng, dropoffLat, dropoffLng, cityId);

// Wrong: call Google Maps directly
// GoogleMapsClient.directions(...)  — FORBIDDEN in pricing-service
```

### For Final Fare Calculation

Use the actual `distanceMetres` and `durationSeconds` from the ride record, passed by
ride-service when calling `/api/v1/pricing/calculate`. No routing call needed.

---

## 7. Caching

- **Pricing rules** (vehicle type configs): Cached in Redis. Invalidated when
  `twende.config.country-updated` Kafka event is received.
- **Surge multipliers**: Stored directly in Redis, updated by the surge scheduler every 60s.
- **Fare calculations themselves are not cached** — they are stateless computations.

---

## 8. Configuration

```yaml
server:
  port: 8088

spring:
  application:
    name: twende-pricing
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_pricing
    username: ${DB_USER:twende}
    password: ${DB_PASSWORD:twende}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379

twende:
  services:
    country-config:
      base-url: ${COUNTRY_CONFIG_URL:http://localhost:8081}
    location:
      base-url: ${LOCATION_SERVICE_URL:http://localhost:8085}
```

---

## 9. Key Implementation Rules

1. **Stateless for fare calculation** — no persistence needed for the fare computation
   itself. Pricing rules come from country-config-service via RestClient, cached in Redis.

2. **BigDecimal only** — all monetary arithmetic uses `BigDecimal`. Never `double` or `float`.

3. **No direct mapping API calls** — for distance/duration estimates, call location-service.
   The pricing service never imports or calls Google Maps, OSRM, or Nominatim.

4. **Surge stacking is capped** — when both demand surge and zone-level surge apply,
   multiply them together but cap the result at `surgeMultiplierCap`.

5. **RESTRICTED zones reject the request** — if pickup or dropoff is in a RESTRICTED zone,
   return an error. Do not calculate a fare.

6. **Tanzania rounding** — TZS has `minorUnits = 0`. Round all final amounts to whole
   shillings using `RoundingMode.HALF_UP`.

7. **No Eureka, no Feign** — use Spring `RestClient` for all inter-service HTTP calls.

8. **Cache invalidation via Kafka** — listen for `twende.config.country-updated` events
   to evict stale pricing rules from Redis cache.

9. **All responses use `ApiResponse<T>` wrapper** — consistent with the platform-wide
   response format.

---

## 10. Testing

### Unit Tests

- `PricingService.calculateFare(...)` — test all edge cases:
  - Minimum fare enforcement (when calculated fare is below minimum)
  - Surge multiplier application
  - Surge cap enforcement
  - Zero-distance rides
  - Airport surcharge addition
  - Zone surge stacking with demand surge
  - TZS rounding to whole shillings
  - RESTRICTED zone rejection

### Integration Tests

- Use Testcontainers for Redis
- Mock country-config-service and location-service responses with WireMock or MockRestServiceServer
- Test end-to-end estimate flow: request -> route lookup -> fare calculation -> response
- Test calculate flow: request with actual distance/duration -> fare calculation -> response

### Test Naming

```java
@Test
void givenBajajInDarEsSalaam_whenEstimateFare_thenReturnsCorrectBreakdown() { ... }

@Test
void givenFareBelowMinimum_whenCalculate_thenMinimumFareApplied() { ... }

@Test
void givenAirportPickup_whenEstimate_thenSurchargeAdded() { ... }

@Test
void givenRestrictedZone_whenEstimate_thenRequestRejected() { ... }
```

### Coverage

- Minimum 80% line coverage enforced by JaCoCo
- Run: `./mvnw verify`
- Excluded from coverage: DTOs, enums, config classes
