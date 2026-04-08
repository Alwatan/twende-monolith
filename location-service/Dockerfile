# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY common-lib common-lib
COPY location-service location-service

# Create stub POMs for sibling modules referenced by parent
RUN for dir in api-gateway auth-service country-config-service user-service driver-service \
    ride-service matching-service pricing-service payment-service subscription-service \
    notification-service loyalty-service rating-service analytics-service compliance-service; do \
    mkdir -p $dir && echo '<project><modelVersion>4.0.0</modelVersion><parent><groupId>tz.co.twende</groupId><artifactId>twende-parent</artifactId><version>1.0.0-SNAPSHOT</version></parent><artifactId>'$dir'</artifactId></project>' > $dir/pom.xml; \
    done

RUN chmod +x mvnw \
    && ./mvnw -pl common-lib install -DskipTests -q \
    && ./mvnw -pl location-service package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S twende && adduser -S twende -G twende
WORKDIR /app
COPY --from=build /workspace/location-service/target/location-service-*.jar app.jar
USER twende
EXPOSE 8087
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8087/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
