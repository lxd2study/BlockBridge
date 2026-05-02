#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v go >/dev/null 2>&1; then
  echo "Go 1.22+ is required but was not found in PATH."
  echo "Install it on Ubuntu with: sudo apt update && sudo apt install -y golang-go"
  echo "Then run: go version"
  exit 127
fi

mkdir -p bin
go build -trimpath -ldflags "-s -w" -o bin/relay-station .
