# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-lib common-lib
COPY notification-service notification-service
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw \
    && ./mvnw -pl common-lib install -DskipTests -q \
    && ./mvnw -pl notification-service package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S twende && adduser -S twende -G twende
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/notification-service/target/notification-service-*.jar app.jar
USER twende
EXPOSE 8091
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -sf http://localhost:8091/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
