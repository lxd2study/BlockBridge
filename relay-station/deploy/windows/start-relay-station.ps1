param(
    [string]$Config = "$PSScriptRoot\..\..\config\station.json"
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\..\.."
$Exe = Join-Path $Root "bin\relay-station.exe"

& $Exe --config $Config
