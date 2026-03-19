#!/bin/bash

# ===========================================
# Pudel Discord Bot - Update Script
# Pulls latest changes from Git and restarts
# ===========================================
#
# HOW THE AUTO-UPDATE SYSTEM WORKS:
#
#   The Pudel Docker container has a built-in auto-update mechanism:
#
#   1. At container startup, if AUTO_UPDATE=true (default), the entrypoint script:
#      - Clones or pulls latest code from GIT_REPO/GIT_BRANCH into /app/src volume
#      - Runs `mvn clean package` to rebuild from source
#      - Copies the fresh JAR to /app/app.jar
#      - Starts the bot from the updated JAR
#
#   2. To trigger an update after pushing to GitHub:
#      a) Run this script:     ./scripts/update.sh
#      b) Or manually:         docker compose restart pudel
#      c) Or via updater:      docker compose run --rm pudel-updater
#      d) Or via cron:         0 */6 * * * cd /path && docker compose restart pudel
#      e) Or via GitHub webhook that calls docker compose restart
#
#   3. The pudel_src Docker volume persists the cloned source between restarts,
#      so subsequent builds are incremental and faster.
#
#   When AUTO_UPDATE=false:
#      - The pre-built JAR from the Docker image is used as-is
#      - To update, rebuild the image: docker compose build --no-cache pudel
#
# ===========================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}   Pudel Discord Bot - Update Script    ${NC}"
echo -e "${BLUE}=========================================${NC}"

# Load environment variables (filter out JAVA_OPTS which contains spaces)
if [ -f .env ]; then
    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%$'\r'}"
        [[ "$line" =~ ^#.*$ ]] && continue
        [[ -z "$line" ]] && continue
        key="${line%%=*}"
        value="${line#*=}"
        [[ -z "$key" ]] && continue
        [[ "$key" == "JAVA_OPTS" ]] && continue
        value="${value%\"}"
        value="${value#\"}"
        export "$key"="$value"
    done < .env
fi

AUTO_UPDATE="${AUTO_UPDATE:-true}"
GIT_BRANCH="${GIT_BRANCH:-main}"

# Check if Ollama is enabled to handle profiles
OLLAMA_ENABLED=$(grep "^OLLAMA_ENABLED=" .env 2>/dev/null | cut -d'=' -f2 | tr -d '"' | tr -d "'" | tr '[:upper:]' '[:lower:]')

# Check if AUTO_UPDATE is enabled
if [ "$AUTO_UPDATE" = "true" ]; then
    echo -e "\n${GREEN}AUTO_UPDATE=true${NC} — the container rebuilds from source on restart."
    echo -e "Simply restarting the pudel container will pull & rebuild automatically.\n"

    echo -e "${YELLOW}[1/2] Restarting pudel container (will pull + rebuild + start)...${NC}"

    if [ "$OLLAMA_ENABLED" = "true" ]; then
        docker compose --profile ollama restart pudel
    else
        docker compose restart pudel
    fi

    echo -e "\n${YELLOW}[2/2] Following logs (Ctrl+C to stop)...${NC}"
    echo -e "${BLUE}The container is now pulling latest code, building, and starting.${NC}\n"
    docker compose logs -f pudel

else
    echo -e "\n${YELLOW}AUTO_UPDATE=false${NC} — must rebuild the Docker image manually.\n"

    echo -e "${YELLOW}[1/5] Fetching latest changes from Git...${NC}"
    git fetch origin ${GIT_BRANCH}

    LOCAL=$(git rev-parse HEAD)
    REMOTE=$(git rev-parse origin/${GIT_BRANCH})

    if [ "$LOCAL" = "$REMOTE" ]; then
        echo -e "${GREEN}Already up to date!${NC}"
        read -p "Do you want to rebuild anyway? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 0
        fi
    fi

    echo -e "\n${YELLOW}[2/5] Pulling latest changes...${NC}"
    git pull origin ${GIT_BRANCH}

    echo -e "\n${YELLOW}[3/5] Stopping current containers...${NC}"
    if [ "$OLLAMA_ENABLED" = "true" ]; then
        docker compose --profile ollama stop
    else
        docker compose stop
    fi

    echo -e "\n${YELLOW}[4/5] Rebuilding Docker images...${NC}"
    docker compose build --no-cache pudel

    echo -e "\n${YELLOW}[5/5] Starting containers...${NC}"
    if [ "$OLLAMA_ENABLED" = "true" ]; then
        echo -e "${BLUE}Starting with Ollama (AI enabled)...${NC}"
        docker compose --profile ollama up -d
    else
        echo -e "${BLUE}Starting without Ollama (AI disabled)...${NC}"
        docker compose up -d
    fi
fi

echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}   Update completed successfully!        ${NC}"
echo -e "${GREEN}=========================================${NC}"

echo -e "\n${BLUE}Checking container status...${NC}"
docker compose ps

echo -e "\n${BLUE}To view logs, run: docker compose logs -f pudel${NC}"
