#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js 20+ is required but was not found in PATH."
  echo "Install it on Ubuntu with NodeSource or your server panel runtime manager."
  exit 127
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "npm is required but was not found in PATH."
  exit 127
fi

npm install
npm run build
