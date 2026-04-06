FROM eclipse-temurin:21-jre-alpine

# Security: patch OS-level vulnerabilities
RUN apk upgrade --no-cache

# Security: run as non-root
RUN addgroup -S twende && adduser -S twende -G twende

WORKDIR /app
COPY target/twende-1.0.0-SNAPSHOT.jar app.jar

RUN chown twende:twende app.jar
USER twende

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
