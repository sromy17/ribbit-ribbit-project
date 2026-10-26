#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -d shared/auth-stub/node_modules ]]; then npm ci --prefix shared/auth-stub; fi
if [[ ! -d frontend/node_modules ]]; then npm ci --prefix frontend; fi
exec node scripts/local-stack.cjs "$@"
