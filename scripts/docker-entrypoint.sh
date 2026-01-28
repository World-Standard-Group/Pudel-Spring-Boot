#!/bin/bash

# ===========================================
# Pudel Discord Bot - Docker Entrypoint
# Handles git clone/update and application startup
# ===========================================

set -e

echo "========================================="
echo "   Pudel Discord Bot - Starting...      "
echo "========================================="

cd /app/src

# Clone or update repository
if [ "$AUTO_UPDATE" = "true" ] || [ ! -f "/app/src/pom.xml" ]; then
    echo "[1/3] Checking for updates from Git..."

    if [ -d "/app/src/.git" ]; then
        echo "Updating existing repository..."
        git fetch origin ${GIT_BRANCH:-main}
        git reset --hard origin/${GIT_BRANCH:-main}
    else
        echo "Cloning repository..."
        rm -rf /app/src/*
        git clone --depth 1 --branch ${GIT_BRANCH:-main} ${GIT_REPO} .
    fi

    echo "[2/3] Building application..."
    mvn clean package -DskipTests -pl pudel-core -am

    echo "Copying built JAR..."
    cp pudel-core/target/*.jar /app/app.jar
else
    echo "[1/3] Skipping update (AUTO_UPDATE=false)"
    echo "[2/3] Using existing build..."
fi

# Copy plugins if they exist in the build
if [ -d "/app/src/plugins" ]; then
    cp /app/src/plugins/*.jar /app/plugins/ 2>/dev/null || true
fi

echo "[3/3] Starting Pudel Bot..."

cd /app

exec java ${JAVA_OPTS} -jar app.jar \
    --spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB} \
    --spring.datasource.username=${POSTGRES_USER} \
    --spring.datasource.password=${POSTGRES_PASS} \
    --server.port=${SERVER_PORT}

