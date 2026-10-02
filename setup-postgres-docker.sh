#!/bin/bash

# Setup PostgreSQL and Spring Boot in Docker containers
# This script creates a PostgreSQL 16 Alpine container + loads database schema + data
# AND builds and runs a Spring Boot application container

set -e

# Configuration
POSTGRES_CONTAINER_NAME="ribbit-postgres"
SPRINGBOOT_CONTAINER_NAME="ribbit-springboot"
DB_PASSWORD="mission"
DB_NAME="mission"
DB_USER="postgres"
DB_PORT="8083"
SPRINGBOOT_PORT="8082"
SCHEMA_FILE="basedSchema.sql"
DATA_FILE="insert-data.sql"
NETWORK_NAME="ribbit-network"
IMAGE_NAME="ribbit-springboot:latest"

echo "Starting PostgreSQL and Spring Boot Docker setup..."

# Create Docker network if it doesn't exist
echo "Setting up Docker network..."
if ! docker network ls --format '{{.Name}}' | grep -q "^${NETWORK_NAME}$"; then
    echo "Creating network: $NETWORK_NAME"
    docker network create "$NETWORK_NAME"
else
    echo "Network $NETWORK_NAME already exists"
fi

# Stop and remove existing containers if they exist
if docker ps -a --format '{{.Names}}' | grep -q "^${POSTGRES_CONTAINER_NAME}$"; then
    echo "Removing existing container: $POSTGRES_CONTAINER_NAME"
    docker stop "$POSTGRES_CONTAINER_NAME" 2>/dev/null || true
    docker rm "$POSTGRES_CONTAINER_NAME" 2>/dev/null || true
fi

if docker ps -a --format '{{.Names}}' | grep -q "^${SPRINGBOOT_CONTAINER_NAME}$"; then
    echo "Removing existing container: $SPRINGBOOT_CONTAINER_NAME"
    docker stop "$SPRINGBOOT_CONTAINER_NAME" 2>/dev/null || true
    docker rm "$SPRINGBOOT_CONTAINER_NAME" 2>/dev/null || true
fi

# Run PostgreSQL container
echo "Starting PostgreSQL container..."
docker run -d \
    --name "$POSTGRES_CONTAINER_NAME" \
    --network "$NETWORK_NAME" \
    -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    -e POSTGRES_DB="$DB_NAME" \
    -p "$DB_PORT":5432 \
    postgres:16-alpine

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
sleep 3

# Copy schema file to container
echo "Loading schema from $SCHEMA_FILE..."
docker cp "$SCHEMA_FILE" "$POSTGRES_CONTAINER_NAME":/schema.sql

# Execute schema
echo "Executing schema..."
docker exec -e PGPASSWORD="$DB_PASSWORD" "$POSTGRES_CONTAINER_NAME" \
    psql -U "$DB_USER" -d "$DB_NAME" -f /schema.sql

# Copy and execute data file
if [ -f "$DATA_FILE" ]; then
    echo "Loading data from $DATA_FILE..."
    docker cp "$DATA_FILE" "$POSTGRES_CONTAINER_NAME":/data.sql
    
    echo "Inserting data..."
    docker exec -e PGPASSWORD="$DB_PASSWORD" "$POSTGRES_CONTAINER_NAME" \
        psql -U "$DB_USER" -d "$DB_NAME" -f /data.sql
else
    echo "Warning: $DATA_FILE not found, skipping data insertion"
fi

echo "✓ PostgreSQL container setup complete!"

# Build Spring Boot Docker image
echo ""
echo "Building Spring Boot Docker image..."
echo "Running Maven build..."
if ! mvn clean package -DskipTests; then
    echo "✗ Maven build failed!"
    exit 1
fi

echo "Building Docker image: $IMAGE_NAME"
if ! docker build -t "$IMAGE_NAME" .; then
    echo "✗ Docker build failed!"
    exit 1
fi
echo "✓ Spring Boot Docker image built successfully!"

# Run Spring Boot container
echo ""
echo "Starting Spring Boot container..."
docker run -d \
    --name "$SPRINGBOOT_CONTAINER_NAME" \
    --network "$NETWORK_NAME" \
    -p "$SPRINGBOOT_PORT":8082 \
    -e "SPRING_DATASOURCE_URL=jdbc:postgresql://$POSTGRES_CONTAINER_NAME:5432/$DB_NAME" \
    -e "SPRING_DATASOURCE_USERNAME=$DB_USER" \
    -e "SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD" \
    "$IMAGE_NAME"

# Wait for Spring Boot to start
echo "Waiting for Spring Boot application to start..."
sleep 5

echo ""
echo "✓ Spring Boot container setup complete!"
echo ""
echo "=========================================="
echo "📊 SETUP COMPLETE!"
echo "=========================================="
echo ""
echo "PostgreSQL Connection Details:"
echo "  Host:      localhost"
echo "  Port:      $DB_PORT"
echo "  Database:  $DB_NAME"
echo "  User:      $DB_USER"
echo "  Password:  $DB_PASSWORD"
echo ""
echo "Spring Boot Connection Details:"
echo "  URL:       http://localhost:$SPRINGBOOT_PORT"
echo "  Database:  jdbc:postgresql://$POSTGRES_CONTAINER_NAME:5432/$DB_NAME"
echo ""
echo "Container Management:"
echo "  PostgreSQL Container: $POSTGRES_CONTAINER_NAME"
echo "  Spring Boot Container: $SPRINGBOOT_CONTAINER_NAME"
echo "  Docker Network: $NETWORK_NAME"
echo ""
echo "Useful Commands:"
echo "  View logs:           docker logs $SPRINGBOOT_CONTAINER_NAME"
echo "  Stop PostgreSQL:     docker stop $POSTGRES_CONTAINER_NAME"
echo "  Stop Spring Boot:    docker stop $SPRINGBOOT_CONTAINER_NAME"
echo "  Start PostgreSQL:    docker start $POSTGRES_CONTAINER_NAME"
echo "  Start Spring Boot:   docker start $SPRINGBOOT_CONTAINER_NAME"
echo "  Remove PostgreSQL:   docker rm $POSTGRES_CONTAINER_NAME (stop it first)"
echo "  Remove Spring Boot:  docker rm $SPRINGBOOT_CONTAINER_NAME (stop it first)"
echo ""

