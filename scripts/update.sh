#!/bin/bash

# ===========================================
# Pudel Discord Bot - Update Script
# Pulls latest changes from Git and rebuilds
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

# Load environment variables
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

GIT_BRANCH=${GIT_BRANCH:-main}

echo -e "\n${YELLOW}[1/5] Fetching latest changes from Git...${NC}"
git fetch origin ${GIT_BRANCH}

# Check if there are updates
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
docker-compose down

echo -e "\n${YELLOW}[4/5] Rebuilding Docker images...${NC}"
docker-compose build --no-cache pudel

echo -e "\n${YELLOW}[5/5] Starting containers...${NC}"
docker-compose up -d

echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}   Update completed successfully!        ${NC}"
echo -e "${GREEN}=========================================${NC}"

echo -e "\n${BLUE}Checking container status...${NC}"
docker-compose ps

echo -e "\n${BLUE}To view logs, run: docker-compose logs -f pudel${NC}"

