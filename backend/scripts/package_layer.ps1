# =============================================================================
# package_layer.ps1
# Empaqueta la Lambda Layer "shared" con sus dependencias Python.
#
# Layer contiene:
#   - shared/ (db, auth, pricing, seasons, distance, responses)
#   - bcrypt (con binario Linux x86_64 para AWS Lambda)
#   - PyJWT (puro Python)
#   - boto3 ya viene en runtime Lambda, no se incluye
#
# IMPORTANTE: instala bcrypt con la wheel para Linux explicitamente.
# Sin esto, pip descarga la wheel de Windows y la layer falla en Lambda.
#
# pyodbc va en una layer SEPARADA (pyodbc313.zip del profesor).
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File .\scripts\package_layer.ps1
#
# Genera:
#   dist\shared_layer.zip
# =============================================================================

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$distFolder = Join-Path $projectRoot "dist"
if (-not (Test-Path $distFolder)) {
    New-Item -ItemType Directory -Path $distFolder | Out-Null
}

Write-Host "Empaquetando layer shared (codigo + bcrypt + PyJWT)..." -ForegroundColor Cyan

$staging = Join-Path $distFolder "_staging_shared_layer"
if (Test-Path $staging) { Remove-Item -Recurse -Force $staging }
$pythonFolder = Join-Path $staging "python"
New-Item -ItemType Directory -Path $pythonFolder -Force | Out-Null

# 1. Instalar bcrypt + PyJWT forzando wheels de Linux x86_64 para Lambda
Write-Host "Descargando bcrypt y PyJWT (wheels Linux x86_64)..." -ForegroundColor Cyan
& py -3.13 -m pip install `
    bcrypt==4.2.1 PyJWT==2.10.1 `
    --target $pythonFolder `
    --platform manylinux2014_x86_64 `
    --python-version 3.13 `
    --only-binary=:all: `
    --no-deps `
    --upgrade

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: pip install fallo. Revisa que py -3.13 este instalado." -ForegroundColor Red
    Write-Host "Verifica con: py -3.13 --version" -ForegroundColor Yellow
    exit 1
}

# 2. Copiar nuestro codigo shared/
Write-Host "Copiando codigo shared/..." -ForegroundColor Cyan
$sharedSource = Join-Path $projectRoot "lambdas\shared"
Copy-Item -Recurse -Path $sharedSource -Destination (Join-Path $pythonFolder "shared")

# 3. Limpiar __pycache__
Get-ChildItem -Path $pythonFolder -Recurse -Directory -Filter "__pycache__" -ErrorAction SilentlyContinue |
    Remove-Item -Recurse -Force

# 4. Comprimir
$sharedZip = Join-Path $distFolder "shared_layer.zip"
if (Test-Path $sharedZip) { Remove-Item -Force $sharedZip }
Compress-Archive -Path "$pythonFolder" -DestinationPath $sharedZip -CompressionLevel Optimal

# 5. Limpieza
Remove-Item -Recurse -Force $staging

$size = (Get-Item $sharedZip).Length / 1KB
Write-Host ""
Write-Host "OK shared_layer.zip ($([math]::Round($size, 1)) KB)" -ForegroundColor Green
Write-Host ""
Write-Host "RECORDATORIO: tambien subir la layer pyodbc del profesor:" -ForegroundColor Yellow
Write-Host "  Archivo: pyodbc313.zip (de Recursos_Semana_5)" -ForegroundColor Yellow
Write-Host "  En AWS Console -> Lambda -> Layers -> Create Layer" -ForegroundColor Yellow
Write-Host "  Compatible runtimes: Python 3.13" -ForegroundColor Yellow
