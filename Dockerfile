# ==============================================================================
# VoltGrid Spring Boot Backend - Production Multi-Stage Dockerfile
# Optimized for Java 21, Container Memory Limits, and Cloud Hosting (Render/Railway/Fly/GCR)
# ==============================================================================

# Build Stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies for faster rebuild caching
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add unprivileged user for security
RUN addgroup -S voltgrid && adduser -S voltgrid -G voltgrid
USER voltgrid:voltgrid

COPY --from=build --chown=voltgrid:voltgrid /app/target/ev-charging-platform-1.0.0.jar app.jar

ENV PORT=8080
EXPOSE 8080

# JVM container support & efficient memory allocation
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
