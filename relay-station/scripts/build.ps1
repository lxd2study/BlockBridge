$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force bin | Out-Null
go build -trimpath -ldflags "-s -w" -o bin\relay-station.exe .
