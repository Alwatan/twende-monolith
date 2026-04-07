# Twende Platform

**Twende** ("Let's Go" in Swahili) is a transport platform for African markets, launching first in Tanzania. It covers three service categories:

- **Rides** — On-demand ride-hailing (Bajaj, Boda Boda, Economy Car)
- **Charter** — Pre-booked group transport for events (minibus/bus, standard/luxury)
- **Cargo** — Pre-booked freight transport (cargo tuk-tuk to heavy trucks)

## What Makes Twende Different

**Flexible revenue model:** Drivers choose between a subscription (keep 100% of earnings) or a flat fee per trip (Twende takes a percentage). Charter and cargo are always flat fee. All customer payments are cash.

**Key features:**
- Driver subscription bundles (daily, weekly, monthly)
- Fare boost — riders increase their offer to attract drivers faster
- Rejection counter — riders see how many drivers passed
- Trip start OTP — 4-digit code for trip verification
- Broadcast-and-accept matching for rides
- Marketplace model for charter and cargo bookings
- Multi-country support (Tanzania first, Kenya and Uganda next)

## Architecture

Microservices monorepo — 16 Spring Boot services + 1 shared library, all in one Maven project.

| Service | Port | Purpose |
|---------|------|---------|
| api-gateway | 8080 | Entry point, JWT validation, rate limiting, routing |
| auth-service | 8081 | OAuth2 Authorization Server, phone OTP login |
| country-config-service | 8082 | Per-country config, vehicle types, feature flags |
| user-service | 8083 | Rider/organizer/shipper profiles |
| driver-service | 8084 | Driver profiles, documents, vehicles |
| ride-service | 8085 | Ride/booking lifecycle orchestration |
| matching-service | 8086 | Driver matching (broadcast for rides, marketplace for charter/cargo) |
| location-service | 8087 | WebSocket tracking, Redis GEO, PostGIS zones |
| pricing-service | 8088 | Fare calculation, surge, charter/cargo pricing |
| payment-service | 8089 | Driver wallets, Selcom mobile money, flat fee deductions |
| subscription-service | 8090 | Driver bundles and flat fee management |
| notification-service | 8091 | Push (FCM), SMS (Africa's Talking), email |
| rating-service | 8092 | Rider and driver ratings |
| analytics-service | 8093 | Business metrics and dashboards |
| compliance-service | 8094 | SUMATRA regulatory reporting |
| loyalty-service | 8095 | Rider loyalty programme, free rides |

## Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 21 (LTS) | Language |
| Spring Boot | 4.0.5 | Framework |
| Spring Cloud | 2025.1.1 (Oakwood) | Gateway, circuit breaker |
| PostgreSQL | 16 | Database (one per service) |
| Redis | 7 | Cache, GEO, rate limiting |
| Apache Kafka | 3.8 | Async event bus |
| Flyway | - | Schema migrations |
| Testcontainers | 1.20.0 | Integration tests |
| JaCoCo | 0.8.12 | Code coverage (80% minimum) |

## Prerequisites

- Java 21 (e.g. Amazon Corretto, Eclipse Temurin)
- Docker Desktop (for local infrastructure and tests)
- [Trivy](https://github.com/aquasecurity/trivy) (for security scanning)

## Quick Start

```bash
# Start local infrastructure (Postgres, Redis, Kafka, Zipkin, MinIO)
make up

# Build everything
./mvnw clean install

# Run a specific service
./mvnw -pl auth-service spring-boot:run

# Run tests for a service
./mvnw -pl auth-service test

# Format code
make format

# Run all pre-push checks (format + test + coverage + security scan)
make check
```

## Project Structure

```
twende-platform/
├── pom.xml                  # Parent POM (dependency management)
├── CLAUDE.md                # Architecture & conventions (for AI-assisted development)
├── SECURITY.md              # Security policy
├── Makefile                 # Build shortcuts
├── docker-compose.yml       # Local dev infrastructure
├── common-lib/              # Shared library (entities, enums, events, utils)
├── api-gateway/             # Spring Cloud Gateway (WebFlux)
├── auth-service/            # OAuth2 + OTP authentication
├── country-config-service/  # Multi-country configuration
├── user-service/            # Rider profiles
├── driver-service/          # Driver profiles, documents, vehicles
├── location-service/        # Real-time tracking, geocoding, zones
├── pricing-service/         # Fare calculation, surge pricing
├── matching-service/        # Driver-rider matching
├── ride-service/            # Ride/booking lifecycle
├── payment-service/         # Wallets, payments, flat fee
├── subscription-service/    # Driver bundles
├── notification-service/    # Push, SMS, email notifications
├── loyalty-service/         # Rider loyalty programme
├── rating-service/          # Ratings
├── analytics-service/       # Business metrics
└── compliance-service/      # Regulatory reporting
```

Each service has its own `CLAUDE.md` with detailed implementation specs.

## Development Workflow

### Before every push

Three mandatory checks (see `make check`):

1. **Format** — `./mvnw spotless:check` (Google Java Format, AOSP style)
2. **Build + Test + Coverage** — `./mvnw clean verify` (all tests, 80% coverage minimum)
3. **Security scan** — `trivy fs --scanners vuln,secret --severity HIGH,CRITICAL .`

### CI/CD Pipeline

| Stage | Tool | What it checks |
|-------|------|----------------|
| Lint & Format | Spotless | Code style |
| Build & Test | Maven + JaCoCo | Tests + 80% coverage |
| Dependency Scan | Trivy | CVEs + secret detection |
| SAST | CodeQL v4 | SQL injection, XSS, etc. |
| Container Build | Docker Buildx | Dockerfile validity |

### Build Phases

| Phase | Services | Status |
|-------|----------|--------|
| 1 — Foundation | common-lib, auth-service, api-gateway | Done |
| 2 — Core Data | country-config-service, user-service, driver-service | Pending |
| 3 — Ride Flow | location, pricing, matching, ride services | Pending |
| 4 — Commerce | payment, subscription, loyalty services | Pending |
| 5 — Supporting | notification, rating services | Pending |
| 6 — Observability | analytics, compliance, admin endpoints | Pending |
| 7 — Flat Fee | Revenue model expansion | Planned |
| 8 — Charter | Group transport bookings | Planned |
| 9 — Cargo | Freight transport bookings | Planned |

## Vehicle Types (Tanzania)

### Rides
- **Bajaj** — Three-wheeler tuk-tuk
- **Boda Boda** — Motorcycle
- **Economy Car** — Standard car

### Charter
- **Minibus Standard/Luxury** — 14-18 passengers
- **Bus Standard/Luxury** — 30-50 passengers

### Cargo
- **Cargo Tuk-tuk** — Up to 500 kg
- **Light Truck** — Up to 3 tonnes
- **Medium Truck** — 3-10 tonnes
- **Heavy Truck** — 10+ tonnes

## Security

See [SECURITY.md](SECURITY.md) for vulnerability reporting.

- OAuth2 JWT authentication (Spring Authorization Server)
- Rate limiting at API Gateway (per-IP and per-user)
- Trivy dependency scanning + CodeQL SAST in CI
- Dependabot alerts for dependency vulnerabilities
- No secrets in code or Docker images

## License

Proprietary. All rights reserved.
