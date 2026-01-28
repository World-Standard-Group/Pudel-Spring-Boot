#!/bin/bash

# ===========================================
# Pudel Discord Bot - Quick Start Script
# One-click setup for new installations
# ===========================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}   Pudel Discord Bot - Quick Start      ${NC}"
echo -e "${BLUE}=========================================${NC}"

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    if ! docker compose version &> /dev/null; then
        echo -e "${RED}Error: Docker Compose is not installed. Please install Docker Compose first.${NC}"
        exit 1
    fi
fi

# Create .env file if it doesn't exist
if [ ! -f .env ]; then
    echo -e "\n${YELLOW}Creating .env file from template...${NC}"
    cp .env.example .env
    echo -e "${YELLOW}Please edit .env file and add your Discord bot token!${NC}"
    echo -e "${YELLOW}Then run this script again.${NC}"
    exit 0
fi

# Check if Discord token is set
if grep -q "your_discord_bot_token_here" .env; then
    echo -e "${RED}Error: Please set your DISCORD_BOT_TOKEN in .env file!${NC}"
    echo -e "Get your token from: https://discord.com/developers/applications"
    exit 1
fi

echo -e "\n${YELLOW}[1/4] Pulling required Docker images...${NC}"
docker-compose pull postgres ollama

echo -e "\n${YELLOW}[2/4] Building Pudel Bot image...${NC}"
docker-compose build pudel

echo -e "\n${YELLOW}[3/4] Starting services...${NC}"
docker-compose up -d postgres

echo -e "${BLUE}Waiting for PostgreSQL to be ready...${NC}"
sleep 10

# Start Ollama if enabled
if grep -q "OLLAMA_ENABLED=true" .env; then
    echo -e "\n${YELLOW}Starting Ollama service...${NC}"
    docker-compose up -d ollama

    echo -e "${BLUE}Waiting for Ollama to be ready...${NC}"
    sleep 10

    # Pull the configured model
    OLLAMA_MODEL=$(grep "OLLAMA_MODEL=" .env | cut -d'=' -f2)
    EMBEDDING_MODEL=$(grep "EMBEDDING_MODEL=" .env | cut -d'=' -f2)

    echo -e "\n${YELLOW}Pulling Ollama models...${NC}"
    docker-compose exec -T ollama ollama pull ${OLLAMA_MODEL:-qwen3:8b} || true
    docker-compose exec -T ollama ollama pull ${EMBEDDING_MODEL:-qwen3-embedding:8b} || true
fi

echo -e "\n${YELLOW}[4/4] Starting Pudel Bot...${NC}"
docker-compose up -d pudel

echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}   Setup completed successfully!         ${NC}"
echo -e "${GREEN}=========================================${NC}"

echo -e "\n${BLUE}Container Status:${NC}"
docker-compose ps

echo -e "\n${BLUE}Useful commands:${NC}"
echo -e "  View logs:     ${YELLOW}docker-compose logs -f pudel${NC}"
echo -e "  Stop all:      ${YELLOW}docker-compose down${NC}"
echo -e "  Restart:       ${YELLOW}docker-compose restart pudel${NC}"
echo -e "  Update:        ${YELLOW}./scripts/update.sh${NC}"

echo -e "\n${BLUE}The bot should be online shortly! Check logs for status.${NC}"

