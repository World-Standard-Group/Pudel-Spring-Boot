# ===========================================
# Pudel Discord Bot - Dockerfile
# Multi-stage build for Spring Boot application
# Base: Ubuntu Linux with Eclipse Temurin JDK 25
# ===========================================

ARG JDK_VENDOR=eclipse-temurin
ARG JDK_VERSION=25
ARG BUILDER_VENDOR=maven
ARG BUILDER_VERSION=3.9.12

ARG GIT_REPO=https://github.com/World-Standard-Group/Pudel-Spring-Boot.git
ARG GIT_BRANCH=main

ARG PUDEL_CORE=pudel-core

# Stage 1: Build Stage
FROM ${BUILDER_VENDOR}:${BUILDER_VERSION}-${JDK_VENDOR}-${JDK_VERSION} AS builder

# Set working directory
WORKDIR /app

# Clone the repository (or use local copy if mounted)
RUN git clone --depth 1 --branch ${GIT_BRANCH} ${GIT_REPO} . || true

# Copy local source if exists (will override cloned files)
COPY . .

# Build the project
RUN mvn clean package -DskipTests -pl "${PUDEL_CORE}" -am

# Stage 2: Runtime Stage
FROM ${JDK_VENDOR}:${JDK_VERSION}-jdk AS runtime

LABEL maintainer="World Standard Group"
LABEL description="Pudel Discord Bot - AI Assistant with Plugin System"
LABEL version="2.2.1"

# Re-declare global ARGs needed in this stage (ARGs don't cross FROM boundaries)
ARG BUILDER_VERSION=3.9.12

# Install required runtime packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    git \
    ca-certificates \
    curl \
    gosu \
    && rm -rf /var/lib/apt/lists/* \
    && gosu nobody true

# Install Maven for auto-update rebuild support
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/${BUILDER_VERSION}/binaries/apache-maven-${BUILDER_VERSION}-bin.tar.gz \
    | tar -xzC /opt \
    && ln -s /opt/apache-maven-${BUILDER_VERSION}/bin/mvn /usr/local/bin/mvn \
    && mvn --version

# Create non-root user for security
RUN groupadd -r pudel && useradd -r -g pudel pudel

# Set working directory
WORKDIR /app

ARG PUDEL_CORE=pudel-core

# Copy the built JAR from builder stage
COPY --from=builder /app/${PUDEL_CORE}/target/*.jar app.jar

# Create directories for plugins, data, logs, and keys
# Keys directory will be mounted at runtime for RSA private/public keys
RUN mkdir -p /app/plugins /app/data /app/logs /app/keys /app/src \
    && chown -R pudel:pudel /app

# Copy entrypoint script
COPY scripts/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Define volumes for persistent data and keys
VOLUME ["/app/plugins", "/app/data", "/app/logs", "/app/keys", "/app/src"]

# Note: We start as root to fix bind mount permissions, then drop to pudel user
# The entrypoint script handles this

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
ENV PUDEL_BRANDING_NAME=
ENV PUDEL_BRANDING_CODENAME=
ENV PUDEL_BRANDING_VERSION=
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
ENV EMBEDDING_ENABLED=true
ENV EMBEDDING_MODEL=qwen3-embedding:8b

# Server Configuration
ENV SERVER_PORT=8080
ENV SWAGGER_ENABLED=false
ENV AUTO_UPDATE=true
ENV GIT_REPO=https://github.com/World-Standard-Group/Pudel-Spring-Boot.git
# Don't forget to change Branch for local/stage env
ENV GIT_BRANCH=main


# JVM Options
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+UseStringDeduplication"

# Expose the application port (Must match .env instead of using Variable)
EXPOSE ${SERVER_PORT}

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/api/bot/status || exit 1

# Start the application via entrypoint script (handles permissions)
ENTRYPOINT ["/docker-entrypoint.sh"]