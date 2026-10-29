# =============================================================================
# Multi-stage Dockerfile for the Spring Boot backend
#
# Stage 1 – builder : uses Maven wrapper to compile and package the fat jar
# Stage 2 – runtime : copies only the jar into a slim JRE image
#
# This keeps the final image small (~200 MB) by discarding the JDK,
# Maven cache, and source code.
# =============================================================================

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /build

# Copy Maven wrapper first so Docker can cache the Maven download layer
# separately from source changes.
COPY .mvn/            .mvn/
COPY mvnw             mvnw
COPY mvnw.cmd         mvnw.cmd
COPY pom.xml          pom.xml

# Make the wrapper executable (it's a shell script on Linux)
RUN chmod +x mvnw

# Download all dependencies without building – creates a cached layer that is
# reused on subsequent builds if pom.xml hasn't changed.
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# Copy source and build the fat jar.
# -DskipTests: no tests per project rules.
COPY src/ src/
RUN ./mvnw package -DskipTests -B --no-transfer-progress

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime

# Non-root user for security best practice
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

WORKDIR /app

# Copy the fat jar produced by Stage 1
COPY --from=builder /build/target/education-management-*.jar app.jar

# Hand ownership to the non-root user
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# JVM tuning for containers:
#   -XX:+UseContainerSupport  – respect cgroup memory/CPU limits (Java 11+)
#   -XX:MaxRAMPercentage=75   – use up to 75% of the container's RAM for heap
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]
