#!/usr/bin/env bash
set -euo pipefail

# Default parameters
ENV_FILE=".env"
LOAD_ONLY=false
MAVEN_GOAL="spring-boot:run"
LOADED_COUNT=0

# Parse command line arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --env-file) ENV_FILE="$2"; shift ;;
        --load-only) LOAD_ONLY=true ;;
        --maven-goal) MAVEN_GOAL="$2"; shift ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

write_info() {
    echo -e "\033[36m[INFO] $1\033[0m"
}

write_pass() {
    echo -e "\033[32m[PASS] $1\033[0m"
}

write_fail() {
    echo -e "\033[31m[FAIL] $1\033[0m"
}

import_env_file() {
    local path="$1"
    if [[ ! -f "$path" ]]; then
        echo "Env file not found: $path" >&2
        return 1
    fi

    LOADED_COUNT=0
    while IFS= read -r line || [[ -n "$line" ]]; do
        # Trim leading/trailing whitespace
        line="$(echo "$line" | xargs)"
        
        # Skip empty lines and comments
        [[ -z "$line" || "$line" == \#* ]] && continue

        # Split by first '='
        if [[ "$line" == *=* ]]; then
            local name="${line%%=*}"
            local value="${line#*=}"

            # Trim name and value
            name="$(echo "$name" | xargs)"
            value="$(echo "$value" | xargs)"

            # Strip surrounding quotes if present
            if [[ ("$value" == \"*\" && "$value" == *\*) || ("$value" == \'*\' && "$value" == \*\') ]]; then
                value="${value:1:${#value}-2}"
            fi

            export "$name"="$value"
            LOADED_COUNT=$((LOADED_COUNT + 1))
        fi
    done < "$path"

    return 0
}

# Main execution logic
main() {
    write_info "Loading environment variables from $ENV_FILE"
    
    if ! import_env_file "$ENV_FILE"; then
        write_fail "Env file not found: $ENV_FILE"
        exit 1
    fi
    
    write_pass "Loaded $LOADED_COUNT variable(s) into this process."

    local required=("SPRING_PROFILES_ACTIVE" "DB_URL" "DB_USER" "DB_PASSWORD")
    local missing=()

    for var_name in "${required[@]}"; do
        if [[ -z "${!var_name:-}" ]]; then
            missing+=("$var_name")
        fi
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        write_fail "Missing required variable(s): ${missing[*]}"
        exit 1
    fi

    write_pass "Required variables are present."

    if [[ "$LOAD_ONLY" == true ]]; then
        write_info "Load-only mode complete. Maven was not started."
        exit 0
    fi

    write_info "Starting application with: mvn $MAVEN_GOAL"
    mvn $MAVEN_GOAL
}

# Execute main and gracefully catch any unhandled issues
if ! main; then
    write_fail "An error occurred during startup."
    exit 1
fi