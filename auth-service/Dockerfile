# ── Stage 1: Build ────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Cache dependencies layer separately from source
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Non-root user for security
RUN groupadd --system cbsgroup && \
    useradd  --system --gid cbsgroup cbsuser

# Copy the fat jar from builder
COPY --from=builder /build/target/auth-service-*.jar app.jar

RUN chown cbsuser:cbsgroup app.jar
USER cbsuser

EXPOSE 8080

# JVM tuning: container-aware heap, Virtual Threads (Java 21)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]