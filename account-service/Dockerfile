# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

RUN java -Djarmode=layertools \
    -jar target/account-service-*.jar extract --destination /build/layers

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S cbs && adduser -S cbs -G cbs
USER cbs
WORKDIR /app

COPY --from=builder /build/layers/dependencies          ./
COPY --from=builder /build/layers/spring-boot-loader    ./
COPY --from=builder /build/layers/snapshot-dependencies ./
COPY --from=builder /build/layers/application           ./

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8082/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", \
    "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]