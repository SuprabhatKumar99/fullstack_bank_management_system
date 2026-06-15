# -----------------------------------------------------------------------
# Stage 1 — Build
# -----------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Cache dependencies layer separately
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# -----------------------------------------------------------------------
# Stage 2 — Runtime
# -----------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="CBS Team"
LABEL service="customer-service"
LABEL version="1.0.0"

# Non-root user for security
RUN addgroup -S cbs && adduser -S cbs -G cbs

WORKDIR /app

COPY --from=builder /build/target/customer-service-*.jar app.jar

RUN chown cbs:cbs app.jar

USER cbs

EXPOSE 8081

# JVM tuning for container environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]