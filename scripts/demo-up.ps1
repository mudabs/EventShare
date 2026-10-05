# One-command local interview demo (docs/DEMO.md).
#   First run:  creates .env.demo with generated passwords, then asks for Clerk/R2 test keys.
#   Next runs:  starts the demo stack on http://localhost:8090 and prints the demo logins.
# Usage:  .\scripts\demo-up.ps1            start (or create .env.demo)
#         .\scripts\demo-up.ps1 -Down      stop the demo stack (data kept)
#         .\scripts\demo-up.ps1 -Wipe      stop it and delete its database volume
param([switch]$Down, [switch]$Wipe)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$EnvFile = '.env.demo'
$Project = 'eventshare-demo'
$Compose = @('compose', '-p', $Project, '--env-file', $EnvFile)

if ($Down) { docker @Compose down; exit 0 }
if ($Wipe) { docker @Compose down -v; exit 0 }

function New-Secret([int]$Length = 24) {
    $chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
    $bytes = New-Object byte[] $Length
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
}

if (-not (Test-Path $EnvFile)) {
    $db = New-Secret 32
    $lines = Get-Content '.env.demo.example' | ForEach-Object {
        if ($_ -match '^(POSTGRES_PASSWORD|SPRING_DATASOURCE_PASSWORD)=GENERATED$') { "$($Matches[1])=$db" }
        elseif ($_ -match '^([A-Z0-9_]+)=GENERATED$') { "$($Matches[1])=Demo-$(New-Secret 16)" }
        else { $_ }
    }
    # LF line endings and no BOM so Docker Compose parses the file the same as on Linux.
    [System.IO.File]::WriteAllText((Join-Path (Get-Location) $EnvFile), (($lines -join "`n") + "`n"))
    Write-Host "Created $EnvFile with generated passwords."
}

$missing = Get-Content $EnvFile | Where-Object { $_ -match '^[A-Z0-9_]+=.*PASTE' } | ForEach-Object { ($_ -split '=')[0] }
if ($missing) {
    Write-Host "Fill these in $EnvFile (Clerk development instance + demo R2 bucket), then run again:"
    $missing | ForEach-Object { Write-Host "  - $_" }
    exit 1
}

docker @Compose up -d --build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

function Get-Val([string]$Key) {
    $line = Get-Content $EnvFile | Where-Object { $_ -match "^$Key=" } | Select-Object -First 1
    if ($line) { $line.Substring($Key.Length + 1) } else { '' }
}
$port = Get-Val 'EVENTSHARE_HTTP_PORT'; if (-not $port) { $port = '8090' }
Write-Host ""
Write-Host "EventShare demo is starting on http://localhost:$port"
Write-Host "(first start builds images and seeds about 20 photos; allow 1 to 3 minutes)"
Write-Host ""
Write-Host "  Guest link   http://localhost:$port/e/$(Get-Val 'DEMO_INVITE_CODE')"
Write-Host "  Host login   username $(Get-Val 'DEMO_HOST_USERNAME')   password $(Get-Val 'DEMO_HOST_PASSWORD')"
Write-Host "  Admin login  username $(Get-Val 'DEMO_ADMIN_USERNAME')  password $(Get-Val 'DEMO_ADMIN_PASSWORD')"
Write-Host "  Promo code   $(Get-Val 'DEMO_PROMO_CODE')"
Write-Host ""
Write-Host "  Logs:  docker compose -p $Project logs -f api"
Write-Host "  Stop:  .\scripts\demo-up.ps1 -Down     Fresh start:  .\scripts\demo-up.ps1 -Wipe"
