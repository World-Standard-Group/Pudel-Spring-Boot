# ===========================================
# Pudel Discord Bot - Dockerfile
# Multi-stage build for Spring Boot application
# Base: Ubuntu Linux with Eclipse Temurin JDK 25
#
# UPDATE WORKFLOW:
#   git pull origin main
#   docker compose build pudel
#   docker compose up -d pudel
# ===========================================

ARG JDK_VENDOR=eclipse-temurin
ARG JDK_VERSION=25
ARG BUILDER_VENDOR=maven
ARG BUILDER_VERSION=3.9.15

ARG PUDEL_CORE=pudel-core

# Stage 1: Build Stage
FROM ${BUILDER_VENDOR}:${BUILDER_VERSION}-${JDK_VENDOR}-${JDK_VERSION} AS builder

WORKDIR /app

# Copy source (always builds from local checkout)
COPY . .

# Build the project
RUN mvn clean package -DskipTests -pl "${PUDEL_CORE}" -am

# Stage 2: Runtime Stage (lean — no Maven, no Git)
FROM ${JDK_VENDOR}:${JDK_VERSION}-jdk AS runtime

LABEL maintainer="World Standard Group"
LABEL description="Pudel Discord Bot - AI Assistant with Plugin System"
LABEL version="2.3.1"

# Install only required runtime packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    gosu \
    && rm -rf /var/lib/apt/lists/* \
    && gosu nobody true

# Create non-root user for security
RUN groupadd -r pudel && useradd -r -g pudel pudel

WORKDIR /app

ARG PUDEL_CORE=pudel-core

# Copy the built JAR from builder stage
COPY --from=builder /app/${PUDEL_CORE}/target/*.jar app.jar

# Create directories for plugins, data, logs, and keys
RUN mkdir -p /app/plugins /app/data /app/logs /app/keys /app/database \
    && chown -R pudel:pudel /app

# Copy entrypoint script
COPY scripts/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Define volumes for persistent data
VOLUME ["/app/plugins", "/app/data", "/app/logs", "/app/keys"]

# ===========================================
# Environment Variables Configuration
# These map to application.yml settings
# ===========================================

# Database Configuration
ENV POSTGRES_HOST=localhost
ENV POSTGRES_PORT=5432
ENV POSTGRES_USER=postgres
ENV POSTGRES_PASS=
ENV POSTGRES_DB=pudel

# Discord Bot Configuration
ENV DISCORD_BOT_TOKEN=
ENV DISCORD_CLIENT_ID=
ENV DISCORD_CLIENT_SECRET=
ENV DISCORD_REDIRECT_URI=http://localhost/auth/callback

# Pudel Configuration
ENV PUDEL_BRANDING_NAME=Pudel
ENV PUDEL_BRANDING_CODENAME="Canis Lupus Familiaris"
ENV PUDEL_BRANDING_VERSION=2.4.0
ENV PUDEL_ADMIN_INITIAL_OWNER=

# JWT Configuration (keys mounted via volume)
ENV JWT_PRIVATE_KEY_PATH=/app/keys/pv.key
ENV JWT_PUBLIC_KEY_PATH=/app/keys/pb.key
ENV JWT_EXPIRATION=604800000

# CORS Configuration
ENV CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000,http://localhost

# Ollama LLM Configuration
ENV OLLAMA_ENABLED=true
ENV OLLAMA_URL=http://localhost:11434
ENV OLLAMA_MODEL=qwen3:8b

# Embedding Configuration
ENV EMBEDDING_MODEL=qwen3-embedding:8b
ENV EMBEDDING_DIMENSION=1024

# Server Configuration
ENV SERVER_PORT=8080
ENV SWAGGER_ENABLED=false
ENV SWAGGER_ACCESS_PROTECTED=false

# JVM Options
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+UseStringDeduplication"

# Expose the application port
EXPOSE ${SERVER_PORT}

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/api/bot/status || exit 1

# Start the application via entrypoint script (handles permissions)
ENTRYPOINT ["/docker-entrypoint.sh"]