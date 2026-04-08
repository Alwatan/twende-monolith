# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-lib common-lib
COPY pricing-service pricing-service
COPY mvnw .
RUN chmod +x mvnw \
    && ./mvnw -pl common-lib install -DskipTests -q \
    && ./mvnw -pl pricing-service package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S twende && adduser -S twende -G twende
WORKDIR /app
COPY --from=build /workspace/pricing-service/target/pricing-service-*.jar app.jar
USER twende
EXPOSE 8088
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8088/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
