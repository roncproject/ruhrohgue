# ─────────────────────────────────────────────────────────────────────────────
# Dockerfile — Hack 1.0.2 AWS Web Edition
# Multi-stage build: compile with Maven → run with slim JRE image.
#
# Build:  docker build -t ruhrohgue .
# Run:    docker run -p 8080:5000 ruhrohgue
# AWS:    push to ECR → deploy to Elastic Beanstalk Docker platform
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

# Download dependencies before copying full source (layer-cache friendly)
COPY pom.xml .
RUN apt-get update -q && apt-get install -y -q maven && \
    mvn dependency:go-offline -B -q

# Copy source and package
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="you@example.com"
LABEL description="Hack 1.0.2 — AWS Web Edition"
LABEL version="1.0.2"

# Non-root user (AWS security best practice)
RUN groupadd -r ruhrohgue && useradd -r -g ruhrohgue -s /bin/false ruhrohgue

WORKDIR /app
COPY --from=build /workspace/target/ruhrohgue.jar ruhrohgue.jar
RUN chown ruhrohgue:ruhrohgue ruhrohgue.jar

USER ruhrohgue

# AWS Elastic Beanstalk maps host port 80 → container port 5000
EXPOSE 5000

# AWS ALB health check target
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -sf http://localhost:5000/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "ruhrohgue.jar"]
