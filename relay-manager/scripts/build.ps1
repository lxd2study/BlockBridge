$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
  throw "Node.js 20+ is required but was not found in PATH."
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
  throw "npm is required but was not found in PATH."
}

npm install
npm run build
