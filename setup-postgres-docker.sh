#!/bin/bash

# Setup PostgreSQL in Docker container
# This script creates a PostgreSQL 16 Alpine container and loads the database schema + data

set -e

# Configuration
CONTAINER_NAME="ribbit-postgres"
DB_PASSWORD="mission"
DB_NAME="mission"
DB_USER="postgres"
DB_PORT="8083"
SCHEMA_FILE="basedSchema.sql"
DATA_FILE="insert-data.sql"

echo "Starting PostgreSQL Docker container setup..."

# Stop and remove existing container if it exists
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "Removing existing container: $CONTAINER_NAME"
    docker stop "$CONTAINER_NAME" 2>/dev/null || true
    docker rm "$CONTAINER_NAME" 2>/dev/null || true
fi

# Run PostgreSQL container
echo "Starting PostgreSQL container..."
docker run -d \
    --name "$CONTAINER_NAME" \
    -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    -e POSTGRES_DB="$DB_NAME" \
    -p "$DB_PORT":5432 \
    postgres:16-alpine

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
sleep 3

# Copy schema file to container
echo "Loading schema from $SCHEMA_FILE..."
docker cp "$SCHEMA_FILE" "$CONTAINER_NAME":/schema.sql

# Execute schema
echo "Executing schema..."
docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER_NAME" \
    psql -U "$DB_USER" -d "$DB_NAME" -f /schema.sql

# Copy and execute data file
if [ -f "$DATA_FILE" ]; then
    echo "Loading data from $DATA_FILE..."
    docker cp "$DATA_FILE" "$CONTAINER_NAME":/data.sql
    
    echo "Inserting data..."
    docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER_NAME" \
        psql -U "$DB_USER" -d "$DB_NAME" -f /data.sql
else
    echo "Warning: $DATA_FILE not found, skipping data insertion"
fi

echo "✓ PostgreSQL container setup complete!"
echo ""
echo "Connection details:"
echo "  Host:     localhost"
echo "  Port:     $DB_PORT"
echo "  Database: $DB_NAME"
echo "  User:     $DB_USER"
echo "  Password: $DB_PASSWORD"
echo ""
echo "Container name: $CONTAINER_NAME"
echo ""
echo "To stop the container: docker stop $CONTAINER_NAME"
echo "To start it again:    docker start $CONTAINER_NAME"
echo "To remove it:         docker rm $CONTAINER_NAME (stop it first)"

