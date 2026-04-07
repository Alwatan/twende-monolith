# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache curl
WORKDIR /workspace

# Copy parent pom, common-lib (real dependency), and auth-service
COPY pom.xml .
COPY common-lib common-lib
COPY auth-service auth-service

# Create minimal pom stubs for sibling modules referenced in parent pom
RUN for mod in api-gateway country-config-service user-service driver-service \
    location-service pricing-service matching-service ride-service payment-service \
    subscription-service notification-service loyalty-service rating-service \
    analytics-service compliance-service; do \
    mkdir -p "$mod"; \
    echo '<project><modelVersion>4.0.0</modelVersion><parent><groupId>com.twende</groupId><artifactId>twende-parent</artifactId><version>1.0.0-SNAPSHOT</version></parent><artifactId>'"$mod"'</artifactId></project>' > "$mod/pom.xml"; \
    done

COPY mvnw .
RUN chmod +x mvnw \
    && ./mvnw -pl common-lib install -DskipTests -q \
    && ./mvnw -pl auth-service package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S twende && adduser -S twende -G twende
WORKDIR /app
COPY --from=build /workspace/auth-service/target/auth-service-*.jar app.jar
USER twende
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
