# CLAUDE.md — Twende API Gateway

> This file is the single source of truth for Claude Code when working on the API Gateway.
> Read it fully before writing any code. Re-read relevant sections before each task.

---

## 1. Overview

The API Gateway is the single entry point for all external traffic into the Twende platform.
Built on **Spring Cloud Gateway** (reactive / WebFlux). It handles:

- Request routing to downstream services
- JWT validation (fetches JWKS from auth-service, validates token signatures)
- Header injection: `X-User-Id`, `X-User-Role`, `X-Country-Code` (extracted from JWT claims)
- Rate limiting (per-IP and per-user, using Redis)
- CORS policy for client apps
- Request/response logging
- Circuit breaking (Resilience4j)

**Port:** 8080
**No database** — fully stateless. Uses Redis only for rate limiting counters.

---

## 2. Critical Constraints

**These are non-negotiable. Never deviate.**

1. **Does NOT depend on common-lib.** Spring Cloud Gateway is WebFlux-based. The common-lib
   uses WebMvc (Spring MVC). Mixing them causes startup failures. If you need a shared class
   (e.g., an enum, a DTO), duplicate it locally in this module rather than adding a common-lib
   dependency.

2. **No Eureka, no Config Server, no Feign.** Service discovery is not used. Routes point to
   downstream services via direct URLs configured through environment variables (e.g.,
   `${AUTH_SERVICE_URL:http://localhost:8081}`). No `spring-cloud-starter-netflix-eureka-client`
   in `pom.xml`.

3. **No database dependency.** No JPA, no Flyway, no Hibernate. This service is stateless.

4. **WebSocket routes (`/ws/**`) bypass the AuthFilter.** Authentication for WebSocket
   connections is handled during the WS handshake inside the location-service via a query
   parameter token (`?token=eyJ...`).

5. **`/internal/**` routes must NOT be exposed externally.** Any route matching `/internal/**`
   must be blocked at the gateway level. Internal service-to-service calls go directly between
   services, not through the gateway.

6. **Health checks are public.** `/actuator/health` is not behind the AuthFilter. It must be
   accessible for container orchestration liveness/readiness probes.

7. **Downstream services trust gateway headers.** Services do NOT re-validate JWTs. They read
   `X-User-Id`, `X-User-Role`, and `X-Country-Code` headers injected by the gateway's
   AuthFilter. The gateway is the sole JWT validator.

---

## 3. Technology Stack

| Technology | Purpose |
|---|---|
| Spring Cloud Gateway | Reactive request routing (WebFlux-based) |
| Spring OAuth2 Resource Server | JWT decoding via JWKS endpoint |
| Spring Data Redis Reactive | Rate limiting counters |
| Resilience4j (reactor) | Circuit breaking for downstream services |
| Micrometer + Prometheus | Metrics at `/actuator/prometheus` |
| Lombok | Boilerplate reduction |

---

## 4. Package Structure

```
tz.co.twende.gateway
├── GatewayApplication.java
├── config/
│   ├── GatewayConfig.java           # Route definitions (Java DSL or YAML supplement)
│   ├── CorsConfig.java              # CORS WebFilter bean
│   ├── RedisConfig.java             # Reactive Redis template for rate limiting
│   ├── SecurityConfig.java          # OAuth2 resource server + JWKS config
│   └── Resilience4jConfig.java      # Circuit breaker and time limiter defaults
├── filter/
│   ├── AuthFilter.java              # JWT validation + header injection (X-User-Id, X-User-Role, X-Country-Code)
│   ├── RoleFilter.java              # Role-based access control (checks X-User-Role)
│   ├── RequestLoggingFilter.java    # Global filter: logs method + path + userId + duration
│   └── InternalRouteBlockFilter.java # Blocks /internal/** from external access
└── resolver/
    ├── IpKeyResolver.java           # Rate limit key: client IP address
    └── UserKeyResolver.java         # Rate limit key: X-User-Id header (falls back to "anonymous")
```

---

## 5. Routing Configuration

Routes are defined in `application.yml`. All service URIs use direct URLs resolved from
environment variables. **No `lb://` scheme** (no Eureka).

### Route Table

| Route ID | Path | Target | Auth | Rate Limit |
|---|---|---|---|---|
| auth-otp | `/api/v1/auth/otp/**` | `${AUTH_SERVICE_URL}` | None (public) | **3 req/s per IP** (brute force protection) |
| auth-service | `/api/v1/auth/**`, `/oauth2/**` | `${AUTH_SERVICE_URL}` | None (public) | 10 req/s per IP |
| country-config-service | `/api/v1/config/**` | `${COUNTRY_CONFIG_SERVICE_URL}` | None (public GET, admin writes inside service) | 10 req/s per IP |
| ride-service | `/api/v1/rides/**` | `${RIDE_SERVICE_URL}` | AuthFilter | **5 req/s per user** (prevent spam requests) |
| location-service | `/api/v1/locations/**` | `${LOCATION_SERVICE_URL}` | AuthFilter | **60 req/s per user** (frequent GPS updates) |
| location-ws | `/ws/**` | `${LOCATION_SERVICE_URL}` | None (WS handshake auth) | None |
| user-service | `/api/v1/users/**` | `${USER_SERVICE_URL}` | AuthFilter | 30 req/s per user |
| driver-service | `/api/v1/drivers/**` | `${DRIVER_SERVICE_URL}` | AuthFilter | 30 req/s per user |
| pricing-service | `/api/v1/pricing/**` | `${PRICING_SERVICE_URL}` | AuthFilter | 30 req/s per user |
| payment-service | `/api/v1/payments/**` | `${PAYMENT_SERVICE_URL}` | AuthFilter | 30 req/s per user |
| subscription-service | `/api/v1/subscriptions/**` | `${SUBSCRIPTION_SERVICE_URL}` | AuthFilter | 30 req/s per user |
| notification-service | `/api/v1/notifications/**` | `${NOTIFICATION_SERVICE_URL}` | AuthFilter | 30 req/s per user |
| loyalty-service | `/api/v1/loyalty/**` | `${LOYALTY_SERVICE_URL}` | AuthFilter | 30 req/s per user |
| rating-service | `/api/v1/ratings/**` | `${RATING_SERVICE_URL}` | AuthFilter | 30 req/s per user |
| matching-service | `/api/v1/matching/**` | `${MATCHING_SERVICE_URL}` | AuthFilter | 30 req/s per user |
| analytics-service | `/api/v1/analytics/**` | `${ANALYTICS_SERVICE_URL}` | AuthFilter + RoleFilter=DRIVER,ADMIN | 30 req/s per user |
| compliance-service | `/api/v1/compliance/**` | `${COMPLIANCE_SERVICE_URL}` | AuthFilter + RoleFilter=ADMIN | 30 req/s per user |

### Blocked Routes

```yaml
# /internal/** must never be routable from outside
- id: block-internal
  predicates:
    - Path=/internal/**
  filters:
    - SetStatus=404
```

---

## 6. Custom Filters

### AuthFilter

Validates the Bearer JWT on every protected route. On success, injects three headers into
the downstream request. On failure, returns 401 immediately.

```java
@Component
public class AuthFilter implements GatewayFilter, Ordered {

    private final ReactiveJwtDecoder jwtDecoder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        return jwtDecoder.decode(token)
            .flatMap(jwt -> {
                ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r
                        .header("X-User-Id", jwt.getSubject())
                        .header("X-User-Role", jwt.getClaimAsString("roles"))
                        .header("X-Country-Code", jwt.getClaimAsString("countryCode")))
                    .build();
                return chain.filter(mutated);
            })
            .onErrorResume(e -> {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            });
    }

    @Override
    public int getOrder() { return -100; }  // runs before other filters
}
```

**Injected headers (downstream services read these):**

| Header | JWT Claim | Description |
|---|---|---|
| `X-User-Id` | `sub` | User UUID (ULID-generated) |
| `X-User-Role` | `roles` | Role string: `RIDER`, `DRIVER`, or `ADMIN` |
| `X-Country-Code` | `countryCode` | ISO 3166-1 alpha-2 (e.g., `TZ`) |

### RoleFilter

A `GatewayFilterFactory` that checks the `X-User-Role` header (set by AuthFilter) against a
list of permitted roles. Returns 403 Forbidden if the role is not in the allowed list.

```java
@Component
public class RoleFilter implements GatewayFilterFactory<RoleFilter.Config> {

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String role = exchange.getRequest().getHeaders().getFirst("X-User-Role");
            if (role == null || !config.getRoles().contains(role)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }

    @Data
    public static class Config {
        private List<String> roles;  // e.g., ["DRIVER", "ADMIN"]
    }
}
```

**Usage in route config:** `RoleFilter=DRIVER,ADMIN`

AuthFilter must run before RoleFilter (AuthFilter order = -100, RoleFilter uses default order).

### RequestLoggingFilter

A global filter applied to every request. Logs: HTTP method, path, userId (from
`X-User-Id` header if present), response status, and request duration in milliseconds.

Implement as a `GlobalFilter` so it applies automatically without per-route configuration.

### InternalRouteBlockFilter

Blocks any request to `/internal/**` with a 404 response. Prevents external callers from
reaching internal service-to-service endpoints that should only be called directly between
services.

---

## 7. Rate Limiting

Uses Spring Cloud Gateway's built-in `RequestRateLimiter` filter backed by Redis.
Per-endpoint rate limits protect sensitive endpoints from abuse while allowing
high-frequency endpoints (like location updates) sufficient throughput.

### Key Resolvers

Both resolvers are in `GatewayKeyResolverConfig`. The `ipKeyResolver` is `@Primary`.

```java
// IP resolver — supports X-Forwarded-For for production behind LB/proxy
@Bean @Primary
public KeyResolver ipKeyResolver() {
    return exchange -> {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return Mono.just(forwarded.split(",")[0].trim()); // first IP = real client
        }
        return Mono.just(exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            : "unknown");
    };
}

// User resolver — uses X-User-Id header injected by AuthFilter
@Bean
public KeyResolver userKeyResolver() {
    return exchange -> Mono.justOrEmpty(
        exchange.getRequest().getHeaders().getFirst("X-User-Id")
    ).defaultIfEmpty("anonymous");
}
```

### Per-Endpoint Limits

| Scope | Replenish Rate | Burst Capacity | Key Resolver | Rationale |
|---|---|---|---|---|
| OTP endpoints (`/auth/otp/**`) | **3 req/s** | 5 | `ipKeyResolver` | Brute force protection |
| Auth general (`/auth/**`, `/oauth2/**`) | 10 req/s | 20 | `ipKeyResolver` | Public, moderate |
| Config (`/config/**`) | 10 req/s | 20 | `ipKeyResolver` | Public, read-mostly |
| Rides (`/rides/**`) | **5 req/s** | 10 | `userKeyResolver` | Prevent spam ride requests |
| Location (`/locations/**`) | **60 req/s** | 120 | `userKeyResolver` | Frequent GPS updates |
| All other authenticated | 30 req/s | 60 | `userKeyResolver` | Standard default |

When rate limited, the gateway returns **429 Too Many Requests** with headers:
`X-RateLimit-Remaining`, `X-RateLimit-Burst-Capacity`, `X-RateLimit-Replenish-Rate`.

---

## 8. CORS Configuration

Environment-aware and configurable via environment variables.

### Behaviour

| Environment | Origins allowed |
|-------------|-----------------|
| **Production** (`TWENDE_ENV=prod`) | `https://admin.twende.co.tz`, `https://app.twende.co.tz` |
| **Development** (default) | Production origins + `http://localhost:3000`, `http://localhost:5173`, `http://localhost:8080` |
| **Custom** (`CORS_ALLOWED_ORIGINS` set) | Comma-separated list from env var (overrides all defaults) |

### Allowed Headers (explicit list, not wildcard)

`Authorization`, `Content-Type`, `Accept`, `X-Requested-With`, `Cache-Control`

### Exposed Headers (visible to browser JavaScript)

`X-RateLimit-Remaining`, `X-RateLimit-Burst-Capacity`, `X-RateLimit-Replenish-Rate`

### Configuration

| Env Var | Default | Description |
|---------|---------|-------------|
| `CORS_ALLOWED_ORIGINS` | (empty, uses defaults) | Comma-separated origins. Overrides all defaults when set. |
| `TWENDE_ENV` | `dev` | Set to `prod` or `production` to exclude localhost origins |

### Rules

- Only allow origins that are known Twende clients
- `allowCredentials: true` is required for cookie-based refresh tokens
- `OPTIONS` preflight requests handled automatically
- `maxAge: 3600` (1 hour) — browser caches preflight response
- Mobile apps (native HTTP) don't need CORS — this is for web clients only

---

## 9. Circuit Breaking

All downstream routes use Resilience4j circuit breakers. When a service fails repeatedly,
the circuit opens and returns a fallback response rather than timing out callers.

```yaml
resilience4j:
  circuitbreaker:
    instances:
      default:
        slidingWindowSize: 10
        failureRateThreshold: 50        # open circuit after 50% failures
        waitDurationInOpenState: 10s     # stay open for 10s before half-open
        permittedNumberOfCallsInHalfOpenState: 3
  timelimiter:
    instances:
      default:
        timeoutDuration: 5s             # max 5s per downstream call
```

When the circuit is open, return HTTP 503 Service Unavailable with a JSON body:
```json
{
  "success": false,
  "message": "Service temporarily unavailable. Please try again shortly.",
  "data": null
}
```

---

## 10. Application Configuration

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  main:
    web-application-type: reactive
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${AUTH_SERVICE_URL:http://localhost:8081}/oauth2/jwks
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
  cloud:
    gateway:
      default-filters:
        - name: CircuitBreaker
          args:
            name: default
            fallbackUri: forward:/fallback
      routes:
        - id: auth-service
          uri: ${AUTH_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/v1/auth/**, /oauth2/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@ipKeyResolver}"

        - id: user-service
          uri: ${USER_SERVICE_URL:http://localhost:8082}
          predicates:
            - Path=/api/v1/users/**
          filters:
            - AuthFilter
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 30
                redis-rate-limiter.burstCapacity: 60
                key-resolver: "#{@userKeyResolver}"

        - id: driver-service
          uri: ${DRIVER_SERVICE_URL:http://localhost:8083}
          predicates:
            - Path=/api/v1/drivers/**
          filters:
            - AuthFilter

        - id: ride-service
          uri: ${RIDE_SERVICE_URL:http://localhost:8084}
          predicates:
            - Path=/api/v1/rides/**
          filters:
            - AuthFilter

        - id: location-service
          uri: ${LOCATION_SERVICE_URL:http://localhost:8085}
          predicates:
            - Path=/api/v1/locations/**
          filters:
            - AuthFilter

        - id: location-ws
          uri: ${LOCATION_SERVICE_WS_URL:ws://localhost:8085}
          predicates:
            - Path=/ws/**
          # No AuthFilter — WS auth on handshake via ?token= query param

        - id: pricing-service
          uri: ${PRICING_SERVICE_URL:http://localhost:8086}
          predicates:
            - Path=/api/v1/pricing/**
          filters:
            - AuthFilter

        - id: payment-service
          uri: ${PAYMENT_SERVICE_URL:http://localhost:8087}
          predicates:
            - Path=/api/v1/payments/**
          filters:
            - AuthFilter

        - id: subscription-service
          uri: ${SUBSCRIPTION_SERVICE_URL:http://localhost:8088}
          predicates:
            - Path=/api/v1/subscriptions/**
          filters:
            - AuthFilter

        - id: notification-service
          uri: ${NOTIFICATION_SERVICE_URL:http://localhost:8089}
          predicates:
            - Path=/api/v1/notifications/**
          filters:
            - AuthFilter

        - id: loyalty-service
          uri: ${LOYALTY_SERVICE_URL:http://localhost:8090}
          predicates:
            - Path=/api/v1/loyalty/**
          filters:
            - AuthFilter

        - id: rating-service
          uri: ${RATING_SERVICE_URL:http://localhost:8091}
          predicates:
            - Path=/api/v1/ratings/**
          filters:
            - AuthFilter

        - id: matching-service
          uri: ${MATCHING_SERVICE_URL:http://localhost:8092}
          predicates:
            - Path=/api/v1/matching/**
          filters:
            - AuthFilter

        - id: analytics-service
          uri: ${ANALYTICS_SERVICE_URL:http://localhost:8093}
          predicates:
            - Path=/api/v1/analytics/**
          filters:
            - AuthFilter
            - RoleFilter=DRIVER,ADMIN

        - id: compliance-service
          uri: ${COMPLIANCE_SERVICE_URL:http://localhost:8094}
          predicates:
            - Path=/api/v1/compliance/**
          filters:
            - AuthFilter
            - RoleFilter=ADMIN

        - id: country-config-service
          uri: ${COUNTRY_CONFIG_SERVICE_URL:http://localhost:8095}
          predicates:
            - Path=/api/v1/config/**
          # Public for GET; admin protection handled inside the service

        - id: block-internal
          uri: no://op
          predicates:
            - Path=/internal/**
          filters:
            - SetStatus=404

resilience4j:
  circuitbreaker:
    instances:
      default:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
  timelimiter:
    instances:
      default:
        timeoutDuration: 5s

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: never

logging:
  level:
    tz.co.twende.gateway: DEBUG
    org.springframework.cloud.gateway: INFO
    org.springframework.security: WARN
```

---

## 11. Environment Variables

| Variable | Default | Description |
|---|---|---|
| `AUTH_SERVICE_URL` | `http://localhost:8081` | Auth service base URL (JWKS + routes) |
| `USER_SERVICE_URL` | `http://localhost:8082` | User service |
| `DRIVER_SERVICE_URL` | `http://localhost:8083` | Driver service |
| `RIDE_SERVICE_URL` | `http://localhost:8084` | Ride service |
| `LOCATION_SERVICE_URL` | `http://localhost:8085` | Location service (HTTP) |
| `LOCATION_SERVICE_WS_URL` | `ws://localhost:8085` | Location service (WebSocket) |
| `PRICING_SERVICE_URL` | `http://localhost:8086` | Pricing service |
| `PAYMENT_SERVICE_URL` | `http://localhost:8087` | Payment service |
| `SUBSCRIPTION_SERVICE_URL` | `http://localhost:8088` | Subscription service |
| `NOTIFICATION_SERVICE_URL` | `http://localhost:8089` | Notification service |
| `LOYALTY_SERVICE_URL` | `http://localhost:8090` | Loyalty service |
| `RATING_SERVICE_URL` | `http://localhost:8091` | Rating service |
| `MATCHING_SERVICE_URL` | `http://localhost:8092` | Matching service |
| `ANALYTICS_SERVICE_URL` | `http://localhost:8093` | Analytics service |
| `COMPLIANCE_SERVICE_URL` | `http://localhost:8094` | Compliance service |
| `COUNTRY_CONFIG_SERVICE_URL` | `http://localhost:8095` | Country config service |
| `REDIS_HOST` | `localhost` | Redis host for rate limiting |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | (empty) | Redis password |
| `CORS_ALLOWED_ORIGINS` | (see CorsConfig) | Comma-separated allowed origins |

---

## 12. Important Notes

### WebSocket Routing
Location service WebSocket connections (`/ws/**`) are routed without the AuthFilter. The
location-service handles authentication during the WS handshake via a query parameter token
(`?token=eyJ...`). The gateway only forwards the connection.

### Health Checks
`/actuator/health` is public (no AuthFilter). Used by Kubernetes liveness/readiness probes
and load balancer health checks. Do not put it behind authentication.

### Internal Traffic
Service-to-service calls go directly between services, NOT through the gateway. The gateway
is for external (client-facing) traffic only. The `/internal/**` route block ensures that
internal endpoints are never accidentally exposed.

### Header Trust Model
Downstream services trust the `X-User-Id`, `X-User-Role`, and `X-Country-Code` headers
set by the AuthFilter. They do NOT re-validate JWTs. This means:
- External clients sending fake `X-User-*` headers are harmless because AuthFilter
  overwrites them with values from the validated JWT.
- Services must never be exposed directly to the internet. All external traffic must
  flow through the gateway.

### No common-lib Dependency
This module is WebFlux-based. The `common-lib` module uses Spring MVC (`spring-boot-starter-web`).
Mixing reactive and servlet stacks causes `BeanCreationException` at startup. If you need a
shared type (enum, DTO, constant), copy it into this module under `tz.co.twende.gateway.shared/`.
Do not add `common-lib` to `pom.xml`.

### Testing
- Use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` with
  `WebTestClient` (not `MockMvc` -- this is a WebFlux application).
- Mock downstream services with `WireMock` or `MockWebServer`.
- Test AuthFilter with valid/invalid/expired JWTs using a test RSA key pair.
- Test rate limiting with a real Redis instance via Testcontainers.

### Formatting
Run `./mvnw spotless:apply` from the `api-gateway/` directory before committing.

---

## Implementation Steps

Build in this order. Each step should compile and pass tests before moving to the next.

- [ ] **1. application.yml** — Port 8080, `spring.main.web-application-type: reactive`, JWKS URI pointing to auth-service (`${AUTH_SERVICE_URL}/oauth2/jwks`), Redis connection for rate limiting, all route definitions with env var base URLs (see section 10 for full config). Include Resilience4j circuit breaker and time limiter defaults. Actuator endpoints (health, metrics, prometheus). Logging levels.
- [ ] **2. GatewayApplication.java** — `@SpringBootApplication` main class. No `@EnableJpaAuditing`, no `@EnableCaching` (this is a stateless gateway).
- [ ] **3. SecurityConfig** — OAuth2 resource server with JWT decoder using JWKS URI from auth-service. Permit `/actuator/health`, `/api/v1/auth/**`, `/oauth2/**` without auth. Disable CSRF (stateless). Configure as reactive security (`@EnableWebFluxSecurity`).
- [ ] **4. AuthFilter** — `GatewayFilter` (order -100). Extract Bearer token from `Authorization` header, decode via `ReactiveJwtDecoder`, inject `X-User-Id` (from `sub`), `X-User-Role` (from `roles` claim), `X-Country-Code` (from `countryCode` claim) into downstream request headers. Return 401 if token missing/invalid/expired.
- [ ] **5. RoleFilter** — `GatewayFilterFactory<RoleFilter.Config>` with configurable `roles` list. Read `X-User-Role` header (set by AuthFilter), return 403 if role not in allowed list. Usage in route config: `RoleFilter=ADMIN` or `RoleFilter=DRIVER,ADMIN`.
- [ ] **6. InternalRouteBlockFilter** — `GlobalFilter` that matches `/internal/**` paths and returns 404. Prevents external access to service-to-service endpoints.
- [ ] **7. RequestLoggingFilter** — `GlobalFilter` that logs HTTP method, path, `X-User-Id` (if present), response status code, and request duration in milliseconds. Runs on every request.
- [ ] **8. Rate limiting** — `GatewayKeyResolverConfig` with `ipKeyResolver` (@Primary, supports X-Forwarded-For for production behind LB) and `userKeyResolver` (X-User-Id header, falls back to "anonymous"). Per-endpoint limits: OTP 3/s per IP, auth 10/s per IP, rides 5/s per user, location 60/s per user, general 30/s per user. Backed by reactive Redis.
- [ ] **9. RedisConfig** — Reactive `ReactiveRedisConnectionFactory` and `ReactiveRedisTemplate` beans for rate limiting counters.
- [ ] **10. CorsConfig** — `CorsWebFilter` bean allowing origins `https://admin.twende.app`, `https://app.twende.app`, `http://localhost:3000`. Allow all standard methods, expose rate limit headers, `allowCredentials: true`, max age 3600s. Support `${CORS_ALLOWED_ORIGINS}` env var override.
- [ ] **11. Resilience4jConfig** — Circuit breaker defaults: sliding window 10, failure rate threshold 50%, wait 10s in open state, 3 calls in half-open. Time limiter: 5s timeout. Fallback controller returning 503 with JSON body `{"success": false, "message": "Service temporarily unavailable. Please try again shortly.", "data": null}`.
- [ ] **12. Route definitions** — All routes per section 5 route table. Auth-service and config GET routes are public (no AuthFilter). WebSocket route `/ws/**` uses `ws://` URI and bypasses AuthFilter. Block `/internal/**` with SetStatus=404. Apply AuthFilter + RoleFilter to admin-only routes (analytics, compliance, config write).
- [ ] **13. Unit tests** — AuthFilter: valid JWT injects correct headers, missing token returns 401, expired token returns 401, malformed token returns 401. RoleFilter: matching role passes, non-matching role returns 403, missing role header returns 403. InternalRouteBlockFilter: `/internal/anything` returns 404. RequestLoggingFilter: verify log output includes method, path, duration.
- [ ] **14. Integration tests** — Testcontainers (Redis). Use `WebTestClient` (not MockMvc). Mock downstream services with WireMock. Test full request flow: valid JWT -> route to downstream -> response returned with correct status. Test rate limiting: send requests exceeding limit, verify 429 response. Test WebSocket route passthrough (no auth required). Test `/internal/**` blocked. Use a test RSA key pair to generate valid/invalid JWTs.
- [ ] **15. Dockerfile** — Use the **Gateway Dockerfile** template from root CLAUDE.md Section 18 (no common-lib dependency). Multi-stage build, eclipse-temurin:21 Alpine, non-root `twende` user, health check on `/actuator/health`, expose port 8080. Build context is the monorepo root.
- [ ] **16. Verify** — Run `./mvnw -pl api-gateway clean verify`. Confirm all tests pass and JaCoCo coverage >= 80% on filter and config classes.
