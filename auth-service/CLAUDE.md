# CLAUDE.md — auth-service

> OAuth2 Authorization Server for the Twende ride-hailing platform.
> Read this fully before writing any code in this module.

---

## 1. Overview

The auth-service is the identity and token provider for the entire Twende platform. It is
built on **Spring Authorization Server** and handles:

- Phone number + 6-digit OTP login (primary authentication for mobile apps)
- OAuth2 JWT access and refresh token issuance
- JWKS endpoint for token validation by all resource servers
- Token revocation (logout via Redis blocklist)
- User registration with Kafka event publishing

**Port:** 8081
**Database:** `twende_auth` (PostgreSQL, managed by Flyway)
**No Eureka. No Feign. No Config Server.** This is a standalone Spring Boot service.

---

## 2. Package Structure

```
tz.co.twende.auth
├── AuthServiceApplication.java
├── config/
│   ├── AuthServerConfig.java         # OAuth2 Authorization Server + JWK source
│   ├── SecurityConfig.java           # HTTP security filter chains
│   ├── RedisConfig.java              # RedisTemplate beans
│   └── KafkaConfig.java              # Kafka producer configuration
├── entity/
│   ├── AuthUser.java                 # Core user identity (phone, role, countryCode)
│   ├── OtpCode.java                  # Short-lived OTP records
│   └── RevokedToken.java             # Refresh token blocklist (DB backup for Redis)
├── repository/
│   ├── AuthUserRepository.java
│   ├── OtpCodeRepository.java
│   └── RevokedTokenRepository.java
├── service/
│   ├── AuthService.java              # Registration, login orchestration
│   ├── OtpService.java               # OTP generation, verification, rate limiting
│   └── TokenService.java             # Token issuance, revocation, blocklist check
├── controller/
│   └── AuthController.java           # REST endpoints for OTP, register, logout, me
├── dto/
│   ├── OtpRequestDto.java
│   ├── OtpVerifyDto.java
│   ├── RegisterRequestDto.java
│   ├── TokenResponseDto.java
│   └── UserInfoDto.java
└── event/
    └── UserRegisteredEvent.java      # Published to Kafka on registration
```

---

## 3. Database Schema

Three application tables plus Spring Authorization Server managed tables.

```sql
-- V1__create_auth_schema.sql

CREATE TABLE auth_users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number   VARCHAR(20) UNIQUE NOT NULL,
    country_code   CHAR(2)     NOT NULL,
    role           VARCHAR(20) NOT NULL,  -- RIDER, DRIVER, ADMIN
    is_active      BOOLEAN     NOT NULL DEFAULT true,
    phone_verified BOOLEAN     NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE otp_codes (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(20)  NOT NULL,
    code         VARCHAR(6)   NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    used         BOOLEAN      NOT NULL DEFAULT false,
    attempts     INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_phone ON otp_codes(phone_number);
CREATE INDEX idx_otp_expires ON otp_codes(expires_at);

-- Refresh token blocklist (Redis is primary, DB is fallback for persistence)
CREATE TABLE revoked_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- Spring Authorization Server managed tables (auto-created by framework):
-- oauth2_registered_client
-- oauth2_authorization
-- oauth2_authorization_consent
```

---

## 4. API Endpoints

### Public (no auth required)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/auth/otp/request` | Request OTP for a phone number |
| `POST` | `/api/v1/auth/otp/verify` | Verify OTP and receive access + refresh tokens |
| `POST` | `/api/v1/auth/register` | Register new account after first OTP verify (requires Bearer token) |
| `POST` | `/oauth2/token` | Standard OAuth2 token endpoint (client credentials, refresh) |
| `GET` | `/oauth2/jwks` | JWKS public keys for token validation by resource servers |

### Authenticated

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/auth/logout` | Revoke refresh token, add JTI to Redis blocklist |
| `GET` | `/api/v1/auth/me` | Returns claims from current access token |
| `PUT` | `/api/v1/auth/change-phone` | Initiate phone number change (requires new OTP) |

### Internal (service-to-service, not routed through gateway)

Internal endpoints are called directly by other services using the `twende-internal`
client credentials grant. They do not pass through the API gateway.

### Request / Response Examples

**Request OTP:**
```json
POST /api/v1/auth/otp/request
{
  "phoneNumber": "+255712345678",
  "countryCode": "TZ"
}
// Response: 200 OK — OTP sent (never reveal if phone is already registered)
```

**Verify OTP:**
```json
POST /api/v1/auth/otp/verify
{
  "phoneNumber": "+255712345678",
  "otp": "123456"
}
// Response:
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "isNewUser": true
  }
}
```

**Register (called after first OTP verify for new users):**
```json
POST /api/v1/auth/register
Authorization: Bearer {accessToken}
{
  "fullName": "Amina Hassan",
  "role": "RIDER",
  "countryCode": "TZ"
}
```

---

## 5. OAuth2 Registered Clients

| Client ID | Grant Types | Usage |
|---|---|---|
| `twende-rider-app` | `password` (phone OTP flow), `refresh_token` | Rider mobile app |
| `twende-driver-app` | `password` (phone OTP flow), `refresh_token` | Driver mobile app |
| `twende-admin` | `authorization_code`, `refresh_token` | Admin web portal |
| `twende-internal` | `client_credentials` | Service-to-service calls |

---

## 6. JWT Token Claims

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "iss": "https://auth.twende.app",
  "iat": 1718000000,
  "exp": 1718003600,
  "roles": ["RIDER"],
  "countryCode": "TZ",
  "phoneVerified": true,
  "scope": "ride:read ride:write profile:read"
}
```

Custom claims (`roles`, `countryCode`, `phoneVerified`) are added via `OAuth2TokenCustomizer<JwtEncodingContext>`.

---

## 7. Service Logic

### OTP Flow

```
1. Client sends POST /api/v1/auth/otp/request { phoneNumber, countryCode }
2. OtpService:
   a. Normalise phone to E.164 via PhoneUtil.normalise() from common-lib
   b. Rate-check: Redis INCR on key "otp:rate:{phoneNumber}", EXPIRE 10 min on first hit
      - If count > 3, throw TooManyRequestsException
   c. Generate 6-digit OTP (SecureRandom)
   d. Save to otp_codes table with 5-minute expiry
   e. Publish OTP delivery request:
      - Dev mode (SMS_DEV_MODE=true): log OTP to console, do NOT send SMS
      - Production: publish Kafka event to notification-service for SMS delivery
   f. Return 200 OK (never reveal whether phone number exists)

3. Client sends POST /api/v1/auth/otp/verify { phoneNumber, otp }
4. OtpService:
   a. Load latest unused OTP for normalised phone number
   b. Check: not expired (5 min TTL), attempts < 3
   c. Increment attempts on each verification attempt
   d. If OTP matches: mark as used, proceed to token issuance
   e. If wrong: save incremented attempts, return error with remaining attempts

5. TokenService:
   a. Look up or create AuthUser for the phone number
   b. Issue OAuth2 access token (1 hour TTL) + refresh token (30 day TTL)
   c. Set isNewUser=true if AuthUser was just created
   d. Return TokenResponseDto

6. If isNewUser=true, client calls POST /api/v1/auth/register
7. AuthService:
   a. Update AuthUser with fullName, role
   b. Set phoneVerified = true
   c. Publish UserRegisteredEvent to Kafka topic twende.users.registered
```

### OTP Rate Limiting (Redis)

```java
String key = "otp:rate:" + phoneNumber;
Long count = redisTemplate.opsForValue().increment(key);
if (count == 1) redisTemplate.expire(key, 10, TimeUnit.MINUTES);
if (count > 3) throw new TooManyRequestsException("Too many OTP requests");
```

### Token Revocation (Logout)

```java
public void logout(String refreshToken) {
    // Decode token to extract JTI and expiry
    // Add JTI to Redis with TTL = remaining token lifetime
    redisTemplate.opsForValue().set("token:revoked:" + jti, "1", remainingTtl, TimeUnit.SECONDS);
    // Also persist to revoked_tokens table for durability
    revokedTokenRepository.save(new RevokedToken(jti, Instant.now(), expiresAt));
}
```

### Refresh Token Rotation

Refresh token rotation is enabled in Spring Authorization Server config. Each use of a
refresh token issues a new refresh token and invalidates the old one. This limits the
window of exposure if a refresh token is compromised.

---

## 8. Kafka Topics

### Published

| Topic | Event | Trigger |
|---|---|---|
| `twende.users.registered` | `UserRegisteredEvent` | New user completes registration via `/api/v1/auth/register` |

**Event payload:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "phoneNumber": "+255712345678",
  "fullName": "Amina Hassan",
  "role": "RIDER",
  "countryCode": "TZ",
  "registeredAt": "2026-04-07T10:30:00Z"
}
```

### Consumed

None. The auth-service is the source of truth for user identities and does not consume
events from other services.

**Important:** Use Kafka (not Spring `ApplicationEventPublisher`) for cross-service events.
`ApplicationEventPublisher` is for intra-process events only in the monolith variant. In this
monorepo microservices architecture, all inter-service communication uses Kafka.

---

## 9. Application Configuration

```yaml
server:
  port: 8081

spring:
  application:
    name: auth-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/twende_auth
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
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

twende:
  auth:
    otp:
      length: 6
      expiry-minutes: 5
      max-attempts: 3
      max-requests-per-window: 3
      window-minutes: 10
      dev-mode: ${SMS_DEV_MODE:true}  # true = log OTP to console, skip SMS
    jwt:
      access-token-ttl-seconds: 3600
      refresh-token-ttl-days: 30
      keystore-path: ${JWT_KEYSTORE_PATH:}
      keystore-password: ${JWT_KEYSTORE_PASSWORD:}
    africastalking:
      api-key: ${AT_API_KEY:}
      username: ${AT_USERNAME:sandbox}
      sender-id: "TWENDE"
```

---

## 10. Security Configuration

```java
@Configuration
public class AuthServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
            .oidc(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = loadOrGenerateRsaKey();  // load from keystore in production
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(AuthUserRepository repo) {
        return context -> {
            if (context.getTokenType().equals(OAuth2TokenType.ACCESS_TOKEN)) {
                Authentication principal = context.getPrincipal();
                AuthUser user = repo.findByPhoneNumber(principal.getName()).orElseThrow();
                context.getClaims()
                    .claim("roles", List.of(user.getRole().name()))
                    .claim("countryCode", user.getCountryCode())
                    .claim("phoneVerified", user.isPhoneVerified());
            }
        };
    }
}
```

### Endpoint Security Rules

- `/api/v1/auth/otp/**` and `/oauth2/**` and `/actuator/health` -> public
- `/api/v1/auth/register` -> authenticated (Bearer token from OTP verify)
- `/api/v1/auth/logout`, `/api/v1/auth/me`, `/api/v1/auth/change-phone` -> authenticated
- All other endpoints -> authenticated

---

## 11. Important Notes

### Phone Number Normalisation
Always store and compare phone numbers in E.164 format (`+255712345678`). Use
`PhoneUtil.normalise(phone, countryCode)` from `common-lib` before any DB lookup or save.
Never accept `0712345678` or `255712345678` without normalisation.

### OTP in Development
Set `SMS_DEV_MODE=true` (default in local config) to log the OTP to console instead of
sending via SMS. This avoids needing Africa's Talking credentials during development.
The OTP is logged at INFO level: `DEV MODE OTP for +255712345678: 123456`.

### Production Keys
The RSA key pair for JWT signing must be loaded from an external keystore (PKCS12 file,
Kubernetes secret, or AWS KMS) in production. Never hardcode or commit private keys.

### SMS Delivery via Kafka
OTP SMS is NOT sent directly by auth-service in the monorepo architecture. Instead,
auth-service publishes an event to Kafka, and the notification-service handles actual SMS
delivery via Africa's Talking (or other providers per country config). In dev mode, this
Kafka publish is skipped entirely and the OTP is logged locally.

### Refresh Token Rotation
Enabled in Spring Authorization Server config. Each refresh token use issues a new one
and invalidates the old. This limits exposure from compromised tokens.

### Money
This service does not handle money, but if any monetary fields are ever added, use
`BigDecimal` only. Never `double` or `float`.

### No Direct Africa's Talking Calls
Despite the source doc listing Africa's Talking as a dependency, the actual SMS delivery
is handled by `notification-service`. Auth-service publishes a Kafka event requesting
OTP delivery. The Africa's Talking dependency should be removed from `pom.xml` if present.

---

## Implementation Steps

Build in this order. Each step should compile and pass tests before moving to the next.

- [ ] **1. application.yml** — Port 8081, datasource `twende_auth`, Kafka producer config, Redis connection, JWT keystore properties (`twende.auth.jwt.*`), OTP config (`twende.auth.otp.*`), dev-mode flag. Include `spring.jpa.hibernate.ddl-auto: validate` and Flyway config.
- [ ] **2. Flyway migration V1__create_auth_schema.sql** — Create `auth_users`, `otp_codes`, and `revoked_tokens` tables exactly as specified in section 3. Add indexes on `otp_codes(phone_number)` and `otp_codes(expires_at)`.
- [ ] **3. Entities** — `AuthUser`, `OtpCode`, `RevokedToken` extending `BaseEntity` from `common-lib`. Map fields to columns per the schema. Use `@Enumerated(EnumType.STRING)` for role. Phone number stored as `VARCHAR(20)`.
- [ ] **4. Repositories** — `AuthUserRepository` (findByPhoneNumber), `OtpCodeRepository` (findTopByPhoneNumberAndUsedFalseOrderByCreatedAtDesc, deleteByExpiresAtBefore), `RevokedTokenRepository` (existsByJti, deleteByExpiresAtBefore).
- [ ] **5. DTOs** — `OtpRequestDto` (phoneNumber, countryCode with validation), `OtpVerifyDto` (phoneNumber, otp), `RegisterRequestDto` (fullName, role, countryCode), `TokenResponseDto` (accessToken, refreshToken, tokenType, expiresIn, isNewUser), `UserInfoDto`.
- [ ] **6. RedisConfig** — `RedisTemplate<String, Object>` bean for OTP rate limiting and token blocklist operations.
- [ ] **7. KafkaConfig** — Kafka producer bean with `StringSerializer` key and `JsonSerializer` value. Define topic constant `twende.users.registered`.
- [ ] **8. OtpService** — `requestOtp()`: normalise phone via `PhoneUtil`, rate-limit check via Redis INCR (max 3 per 10 min), generate 6-digit OTP (SecureRandom), save to `otp_codes` with 5-min expiry, in dev mode log OTP else publish Kafka event for SMS delivery. `verifyOtp()`: load latest unused OTP for phone, check not expired, increment attempts (max 3), match code, mark as used on success.
- [ ] **9. TokenService** — `issueTokens()`: programmatically create OAuth2 access token (1h TTL) + refresh token (30d TTL) using Spring Authorization Server APIs. `revokeToken()`: decode refresh token, extract JTI + expiry, add to Redis blocklist with TTL = remaining lifetime, persist to `revoked_tokens` table. `isRevoked()`: check Redis blocklist for JTI.
- [ ] **10. Spring Authorization Server config (AuthServerConfig)** — RSA key pair (load from keystore in prod, generate in-memory for dev). JWKSource bean. RegisteredClientRepository with 4 clients: `twende-rider-app`, `twende-driver-app`, `twende-admin`, `twende-internal`. OAuth2TokenCustomizer to add `roles`, `countryCode`, `phoneVerified` claims. Enable refresh token rotation.
- [ ] **11. AuthService** — `register()`: validate Bearer token, update AuthUser with fullName/role, set phoneVerified=true, publish `UserRegisteredEvent` to Kafka topic `twende.users.registered`. `getCurrentUser()`: extract claims from security context, return UserInfoDto.
- [ ] **12. SecurityConfig** — Two filter chains: (1) Authorization Server chain at `@Order(1)`, (2) default chain at `@Order(2)`. Public endpoints: `/api/v1/auth/otp/**`, `/oauth2/**`, `/actuator/health`. Authenticated: `/api/v1/auth/register`, `/api/v1/auth/logout`, `/api/v1/auth/me`, `/api/v1/auth/change-phone`. CSRF disabled for stateless API.
- [ ] **13. AuthController** — `POST /api/v1/auth/otp/request` (public), `POST /api/v1/auth/otp/verify` (public), `POST /api/v1/auth/register` (authenticated), `POST /api/v1/auth/logout` (authenticated), `GET /api/v1/auth/me` (authenticated). All responses wrapped in `ApiResponse<T>`.
- [ ] **14. UserRegisteredEvent** — Kafka event class with fields: userId, phoneNumber, fullName, role, countryCode, registeredAt. Serialized as JSON.
- [ ] **15. Scheduled cleanup** — `@Scheduled` job to delete expired OTPs (`otp_codes` where `expires_at < now()`) and expired revoked tokens (`revoked_tokens` where `expires_at < now()`). Run daily.
- [ ] **16. Unit tests** — OtpService: OTP generation, rate limiting (mock Redis), expiry/attempts logic, phone normalisation. TokenService: token issuance, revocation, blocklist check. AuthService: registration flow, duplicate registration guard. Aim for edge cases: expired OTP, max attempts, invalid phone format.
- [ ] **17. Integration tests** — Testcontainers (PostgreSQL + Redis + Kafka). Full OTP flow: request -> verify -> receive tokens. Registration flow: verify OTP -> register -> check Kafka event published. Token revocation: logout -> verify token is rejected. Rate limiting: send 4 OTP requests, verify 4th is rejected (429).
- [ ] **18. Dockerfile** — Multi-stage build (eclipse-temurin:21-jdk-alpine for build, 21-jre-alpine for run). Non-root `twende` user. Health check on `/actuator/health`. Expose port 8081.
- [ ] **19. OpenAPI config** — `OpenApiConfig.java` with SpringDoc `OpenAPI` bean. Title: "Auth Service API". Swagger UI at `/swagger-ui.html`.
- [ ] **20. Verify** — Run `./mvnw -pl auth-service clean verify`. Confirm all tests pass and JaCoCo coverage >= 80% on non-entity/DTO/config classes.
