#!/bin/bash

# ===========================================
# Pudel Discord Bot - Docker Entrypoint
# Handles permissions fix and application startup
#
# UPDATE WORKFLOW (from the host):
#   git pull origin main
#   docker compose build pudel
#   docker compose up -d pudel
# ===========================================

set -e

echo "========================================="
echo "   Pudel Discord Bot - Starting...      "
echo "========================================="

# ===========================================
# [1/2] Fix permissions for bind-mounted directories
# ===========================================
echo "[1/2] Fixing directory permissions..."

for dir in /app/plugins /app/data /app/logs; do
    if [ -d "$dir" ]; then
        chown -R pudel:pudel "$dir" 2>/dev/null || true
        chmod -R 755 "$dir" 2>/dev/null || true
    fi
done

echo "      Permissions fixed"

# ===========================================
# [2/2] Start the application
# ===========================================
echo "[2/2] Starting Pudel Bot..."

cd /app

# Sanitize environment variables
# Strip Windows carriage returns (\r) that may leak from .env files edited on Windows
# Strip literal quotes from JAVA_OPTS that Docker Compose may pass through from .env
POSTGRES_HOST=$(echo "${POSTGRES_HOST}" | tr -d '\r')
POSTGRES_PORT=$(echo "${POSTGRES_PORT}" | tr -d '\r')
POSTGRES_USER=$(echo "${POSTGRES_USER}" | tr -d '\r')
POSTGRES_PASS=$(echo "${POSTGRES_PASS}" | tr -d '\r')
POSTGRES_DB=$(echo "${POSTGRES_DB}" | tr -d '\r')
POSTGRES_SSL_MODE=$(echo "${POSTGRES_SSL_MODE}" | tr -d '\r')
POSTGRES_SSL_CA_CERT=$(echo "${POSTGRES_SSL_CA_CERT}" | tr -d '\r')
POSTGRES_SSL_CLIENT_CERT=$(echo "${POSTGRES_SSL_CLIENT_CERT}" | tr -d '\r')
POSTGRES_SSL_CLIENT_KEY=$(echo "${POSTGRES_SSL_CLIENT_KEY}" | tr -d '\r')
SERVER_PORT=$(echo "${SERVER_PORT}" | tr -d '\r')
JAVA_OPTS=$(echo "${JAVA_OPTS}" | tr -d '\r' | tr -d '"')

# Debug: Print resolved database configuration (password masked)
PASS_MASKED=$(echo "${POSTGRES_PASS}" | sed 's/./*/g')
echo "Database config: host=${POSTGRES_HOST:-unset}, port=${POSTGRES_PORT:-unset}, db=${POSTGRES_DB:-unset}, user=${POSTGRES_USER:-unset}, pass=${PASS_MASKED:-unset}"
echo "JAVA_OPTS: ${JAVA_OPTS}"

# Build Spring Boot arguments only for variables that are actually set
# This prevents overriding application.yml defaults with empty values
SPRING_ARGS=""

if [ -n "${POSTGRES_HOST}" ] && [ -n "${POSTGRES_PORT}" ] && [ -n "${POSTGRES_DB}" ]; then
    DB_URL="jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"

    # Append SSL/mTLS query params. When a full CA + client cert + client key
    # trio is provided we hand TLS off to Pudel's own SSLSocketFactory
    # (javax.net.ssl.SSLSocketFactory implementation, wired via sslfactory=).
    # This bypasses PgJDBC's built-in PEM loader, which only accepts RSA client
    # keys and enforces a 0600 file-permission check; our factory loads any key
    # algorithm (RSA, Ed25519, EC) from read-only mounts. The three PEM paths
    # are passed as JVM system properties (-Dpudel.ssl.*) since the factory is
    # instantiated by PgJDBC with a no-arg constructor.
    SSL_QUERY=""
    [ -n "${POSTGRES_SSL_MODE}" ] && SSL_QUERY="${SSL_QUERY}&sslmode=${POSTGRES_SSL_MODE}"

    if [ -n "${POSTGRES_SSL_CA_CERT}" ] && [ -n "${POSTGRES_SSL_CLIENT_CERT}" ] && [ -n "${POSTGRES_SSL_CLIENT_KEY}" ]; then
        SSL_QUERY="${SSL_QUERY}&sslfactory=group.worldstandard.pudel.core.config.database.PudelPgSSLFactory&channelBinding=require"
        # Inject the PEM paths as JVM system properties for the no-arg factory.
        JAVA_OPTS="${JAVA_OPTS} -Dpudel.ssl.ca=${POSTGRES_SSL_CA_CERT} -Dpudel.ssl.cert=${POSTGRES_SSL_CLIENT_CERT} -Dpudel.ssl.key=${POSTGRES_SSL_CLIENT_KEY}"
    else
        [ -n "${POSTGRES_SSL_CA_CERT}" ]     && SSL_QUERY="${SSL_QUERY}&sslrootcert=${POSTGRES_SSL_CA_CERT}"
        [ -n "${POSTGRES_SSL_CLIENT_CERT}" ] && SSL_QUERY="${SSL_QUERY}&sslcert=${POSTGRES_SSL_CLIENT_CERT}"
        [ -n "${POSTGRES_SSL_CLIENT_KEY}" ]  && SSL_QUERY="${SSL_QUERY}&sslkey=${POSTGRES_SSL_CLIENT_KEY}"
        SSL_QUERY="${SSL_QUERY}&channelBinding=require"
    fi

    if [ -n "${SSL_QUERY}" ]; then
        # Replace the leading '&' with '?' to start the query string.
        DB_URL="${DB_URL}?${SSL_QUERY#&}"
    fi

    SPRING_ARGS="${SPRING_ARGS} --spring.datasource.url=${DB_URL}"
fi

if [ -n "${POSTGRES_USER}" ]; then
    SPRING_ARGS="${SPRING_ARGS} --spring.datasource.username=${POSTGRES_USER}"
fi

if [ -n "${POSTGRES_PASS}" ]; then
    SPRING_ARGS="${SPRING_ARGS} --spring.datasource.password=${POSTGRES_PASS}"
fi

if [ -n "${SERVER_PORT}" ]; then
    SPRING_ARGS="${SPRING_ARGS} --server.port=${SERVER_PORT}"
fi

echo "Spring args: $(echo "${SPRING_ARGS}" | sed 's/--spring.datasource.password=[^ ]*/--spring.datasource.password=****/g')"

# Drop privileges and run as pudel user
# Note: JAVA_OPTS is intentionally unquoted to allow word splitting of JVM flags
exec gosu pudel java ${JAVA_OPTS} -jar app.jar ${SPRING_ARGS}

