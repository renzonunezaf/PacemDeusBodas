# =============================================================================
# load_database.ps1
# Carga el esquema, stored procedures y seeds en la BD SQL Server RDS.
#
# REQUIERE: sqlcmd instalado en Windows
#   Viene con SSMS (SQL Server Management Studio): https://aka.ms/ssmsfullsetup
#   Tambien con SQL Server Command Line Utilities standalone.
#   Verifica con: sqlcmd -? (deberia listar la ayuda)
#
# Uso:
#   $env:DB_PWD = "tu-password-rds"
#   powershell -ExecutionPolicy Bypass -File .\scripts\load_database.ps1 ``
#       -DbServer "pacem-db.xxx.us-east-1.rds.amazonaws.com" ``
#       -DbName "pacem_deus_bodas" ``
#       -DbUser "admin"
#
# Ejecuta los 11 archivos SQL en orden estricto.
# =============================================================================

param(
    [Parameter(Mandatory=$true)] [string]$DbServer,
    [Parameter(Mandatory=$true)] [string]$DbName,
    [Parameter(Mandatory=$true)] [string]$DbUser,
    [int]$DbPort = 1433
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command sqlcmd -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: sqlcmd no esta instalado o no esta en PATH" -ForegroundColor Red
    Write-Host "Instala SSMS desde: https://aka.ms/ssmsfullsetup" -ForegroundColor Yellow
    Write-Host "(SSMS incluye sqlcmd. Reinicia PowerShell despues de instalar.)" -ForegroundColor Yellow
    exit 1
}

if (-not $env:DB_PWD) {
    Write-Host "ERROR: Variable DB_PWD no esta seteada" -ForegroundColor Red
    Write-Host 'Ejecuta antes:  $env:DB_PWD = "tu-password"' -ForegroundColor Yellow
    exit 1
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$sqlFolder = Join-Path $projectRoot "infra\sql"

# Orden estricto de ejecucion
$sqlFiles = @(
    "01_schema.sql",
    "02_procs.sql",
    "03_seed_pricing.sql",
    "04_seed_instruments.sql",
    "05_seed_moments.sql",
    "06_seed_seasons.sql",
    "07_seed_season_dates.sql",
    "08_seed_songs.sql",
    "09_seed_song_moments.sql",
    "10_seed_song_requirements.sql",
    "11_seed_song_styles.sql"
)

Write-Host "Cargando esquema y seeds en ${DbServer}:${DbPort}/$DbName ..." -ForegroundColor Cyan
Write-Host ""

# -S: server, -U: user, -P: password, -d: database, -i: input file, -b: stop on error
$serverArg = "$DbServer,$DbPort"

foreach ($file in $sqlFiles) {
    $fullPath = Join-Path $sqlFolder $file
    if (-not (Test-Path $fullPath)) {
        Write-Host "SKIP: $file no existe" -ForegroundColor Yellow
        continue
    }
    $size = (Get-Item $fullPath).Length / 1KB
    Write-Host "Ejecutando: $file ($([math]::Round($size, 1)) KB)" -ForegroundColor White

    & sqlcmd -S $serverArg -U $DbUser -P $env:DB_PWD -d $DbName -i $fullPath -b -C

    if ($LASTEXITCODE -ne 0) {
        Write-Host "FALLO en $file" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

Write-Host ""
Write-Host "Carga completa. Verificando contenido..." -ForegroundColor Green

$verifyQuery = @"
SELECT
    (SELECT COUNT(*) FROM usuario) AS usuarios,
    (SELECT COUNT(*) FROM instrumento) AS instrumentos,
    (SELECT COUNT(*) FROM momento_liturgico) AS momentos,
    (SELECT COUNT(*) FROM temporada_liturgica) AS temporadas,
    (SELECT COUNT(*) FROM cancion) AS canciones,
    (SELECT COUNT(*) FROM cancion_momento) AS pares_cancion_momento,
    (SELECT COUNT(*) FROM cancion_requerimiento) AS requerimientos,
    (SELECT COUNT(*) FROM configuracion_precios) AS pricing_config
"@

& sqlcmd -S $serverArg -U $DbUser -P $env:DB_PWD -d $DbName -Q $verifyQuery -C
