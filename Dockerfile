# ============================================================================
# Multi-stage Dockerfile
#
# Stage 1 (builder): compila y zipea
# Stage 2 (runtime): imagen con JRE 21
# ============================================================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY gradle/         gradle/
COPY gradlew         gradlew
COPY build.gradle    build.gradle
COPY settings.gradle settings.gradle

RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon -q

COPY src/ src/

# Compila y empaqueta (sin tests para el build de imagen)
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# Metadata
LABEL description="Tenpo Challenge API – Dynamic Percentage Calculator"
LABEL version="1.0.0"

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM flags optimizados para contenedores:
# -XX:+UseContainerSupport           → detecta los limites de CPU/RAM del contenedor
# -XX:MaxRAMPercentage=75.0          → usa hasta el 75% de la RAM del contenedor
# -XX:+UseG1GC                       → Garbage Collector balanceado para latencia y rendimiento
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/urandom -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-docker} ${JAVA_OPTS} -jar app.jar"]
