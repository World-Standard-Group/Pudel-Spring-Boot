# ===========================================
# Pudel Discord Bot - Dockerfile
# Multi-stage build for Spring Boot application
# Base: Ubuntu Linux with Eclipse Temurin JDK 25
# ===========================================

# Stage 1: Build Stage
FROM ubuntu:24.04 AS builder

# Install required packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    git \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install Eclipse Temurin JDK 25 (or latest EA version)
# Note: Adjust version as needed when JDK 25 GA releases
ARG JDK_VERSION=25
RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor > /etc/apt/trusted.gpg.d/adoptium.gpg \
    && echo "deb https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update \
    && apt-get install -y temurin-${JDK_VERSION}-jdk || apt-get install -y temurin-24-jdk \
    && rm -rf /var/lib/apt/lists/*

# Install Maven
ARG MAVEN_VERSION=3.9.9
RUN wget -qO- https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    | tar xzf - -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven

ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# Set working directory
WORKDIR /app

# Clone the repository (or use local copy if mounted)
ARG GIT_REPO=https://github.com/World-Standard-Group/Pudel-Spring-Boot.git
ARG GIT_BRANCH=main

RUN git clone --depth 1 --branch ${GIT_BRANCH} ${GIT_REPO} . || true

# Copy local source if exists (will override cloned files)
COPY . .

# Build the project
RUN mvn clean package -DskipTests -pl pudel-core -am

# Stage 2: Runtime Stage
FROM ubuntu:24.04 AS runtime

LABEL maintainer="World Standard Group"
LABEL description="Pudel Discord Bot - AI Assistant with Plugin System"
LABEL version="1.0.0"

# Install required runtime packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    git \
    ca-certificates \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Eclipse Temurin JRE 25
ARG JDK_VERSION=25
RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor > /etc/apt/trusted.gpg.d/adoptium.gpg \
    && echo "deb https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update \
    && apt-get install -y temurin-${JDK_VERSION}-jre || apt-get install -y temurin-24-jre \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r pudel && useradd -r -g pudel pudel

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/pudel-core/target/*.jar app.jar

# Create directories for plugins and data
RUN mkdir -p /app/plugins /app/data /app/logs \
    && chown -R pudel:pudel /app

# Switch to non-root user
USER pudel

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

# JWT Configuration
ENV JWT_SECRET=change-this-secret-key-in-production-min-32-chars
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

# JVM Options
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+UseStringDeduplication"

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

# Start the application
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar \
    --spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB} \
    --spring.datasource.username=${POSTGRES_USER} \
    --spring.datasource.password=${POSTGRES_PASS} \
    --server.port=${SERVER_PORT}"]

