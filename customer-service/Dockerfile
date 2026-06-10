# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Cache Maven dependencies first (only re-downloads when pom.xml changes)
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

# Build the application
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

# Extract layers for optimised Docker caching
RUN java -Djarmode=layertools \
    -jar target/customer-service-*.jar extract --destination /build/layers

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S cbs && adduser -S cbs -G cbs
USER cbs

WORKDIR /app

# Copy layers in dependency order (least → most likely to change)
COPY --from=builder /build/layers/dependencies          ./
COPY --from=builder /build/layers/spring-boot-loader    ./
COPY --from=builder /build/layers/snapshot-dependencies ./
COPY --from=builder /build/layers/application           ./

# Expose service port
EXPOSE 8081

# Health check for Docker Compose / ECS
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1

# JVM flags tuned for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", \
    "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]