# ── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first — lets Docker cache dependencies layer
# Only re-downloads deps if pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Run ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user — never run as root in production
RUN addgroup -S taskpilot && adduser -S taskpilot -G taskpilot

# Copy only the built jar from Stage 1
COPY --from=builder /app/target/*.jar app.jar

# Own the file as our non-root user
RUN chown taskpilot:taskpilot app.jar

USER taskpilot

# Tell Docker this container listens on 8081
EXPOSE 8081

# JVM flags explained:
# -XX:+UseContainerSupport        → JVM respects Docker memory limits
# -XX:MaxRAMPercentage=75.0       → Use max 75% of container RAM for heap
# -Djava.security.egd=...         → Faster startup (avoids entropy blocking)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]