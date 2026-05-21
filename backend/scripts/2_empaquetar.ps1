# ============================================================================
# 2_empaquetar.ps1
#
# Construye los 2 ZIPs que se suben a AWS Lambda:
#
#   1. pacem-deus-shared-layer-v<N>.zip
#      - Codigo de lambdas/shared/*.py + bcrypt + PyJWT
#      - Se sube como "Layer" en Lambda y se asocia a la funcion
#
#   2. pacem-deus-api-v<N>.zip
#      - TODOS los archivos .py de lambdas/ (admin/, auth/, bodas/, etc.)
#      - "Aplanados" al root del ZIP porque el handler principal
#        (lambda_function.py) importa con `from <modulo> import ...`
#        sin namespace de carpetas.
#
# Output: ambos ZIPs quedan en dist/ con la version siguiente al ultimo
# encontrado en esa carpeta.
#
# COMO EJECUTAR:
#   cd C:\ProyectosAndroidStudio\TB02\pacem-deus-api\scripts
#   .\2_empaquetar.ps1
# ============================================================================

$ErrorActionPreference = "Stop"

$root      = Split-Path -Parent $PSScriptRoot
$lambdasDir = Join-Path $root "lambdas"
$distDir   = Join-Path $root "dist"
$stagingDir = Join-Path $root "dist\_staging_build"

# Asegurar dist/
if (-not (Test-Path $distDir)) { New-Item -ItemType Directory -Path $distDir | Out-Null }

# Limpieza de staging anterior
if (Test-Path $stagingDir) { Remove-Item -Recurse -Force $stagingDir }
New-Item -ItemType Directory -Path $stagingDir | Out-Null

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  Empaquetado de Layer + Lambda" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan

# ----------------------------------------------------------------------------
# Determinar version siguiente para Layer y Lambda
# ----------------------------------------------------------------------------
function Get-NextVersion {
    param ($Pattern)
    $existing = Get-ChildItem $distDir -Filter $Pattern -File -ErrorAction SilentlyContinue |
                ForEach-Object {
                    if ($_.Name -match "v(\d+)") { [int]$Matches[1] }
                } | Sort-Object -Descending
    if ($existing.Count -gt 0) { return $existing[0] + 1 } else { return 1 }
}

$layerVersion  = Get-NextVersion "pacem-deus-shared-layer-v*.zip"
$lambdaVersion = Get-NextVersion "pacem-deus-api-v*.zip"

$layerZip  = Join-Path $distDir "pacem-deus-shared-layer-v$layerVersion.zip"
$lambdaZip = Join-Path $distDir "pacem-deus-api-v$lambdaVersion.zip"

Write-Host ""
Write-Host "  Layer  -> $(Split-Path -Leaf $layerZip)"
Write-Host "  Lambda -> $(Split-Path -Leaf $lambdaZip)"
Write-Host ""

# ----------------------------------------------------------------------------
# LAYER: shared + dependencias
# ----------------------------------------------------------------------------
Write-Host "[1/2] Empaquetando LAYER..." -ForegroundColor Gray

$layerStaging = Join-Path $stagingDir "layer"
$layerPython  = Join-Path $layerStaging "python"
$layerShared  = Join-Path $layerPython "shared"
New-Item -ItemType Directory -Path $layerShared -Force | Out-Null

# Copiar shared/*.py - usar Get-ChildItem porque Copy-Item con wildcard
# en string compuesto falla silenciosamente en PowerShell 5.1 (PS quirk
# documentado en directivas).
Get-ChildItem -Path (Join-Path $lambdasDir "shared") -Filter "*.py" -File |
    ForEach-Object { Copy-Item -Path $_.FullName -Destination $layerShared -Force }

$sharedCount = (Get-ChildItem -Path $layerShared -Filter "*.py" -File).Count
Write-Host "  Copiados $sharedCount archivos .py a shared/ del layer" -ForegroundColor Gray

# Verificar que db.py tiene el converter DATETIMEOFFSET
$dbContent = Get-Content (Join-Path $layerShared "db.py") -Raw
if ($dbContent -notmatch "add_output_converter") {
    Write-Host "  ERROR: db.py no tiene add_output_converter. Abortando." -ForegroundColor Red
    exit 1
}
Write-Host "  Check: db.py tiene add_output_converter (DATETIMEOFFSET fix) OK" -ForegroundColor Green

# Reutilizar bcrypt + PyJWT si ya estan extraidos en el staging permanente
$prevLayerStaging = Join-Path $distDir "_staging_shared_layer\python"
if (Test-Path $prevLayerStaging) {
    Write-Host "  Copiando bcrypt + PyJWT + cryptography del staging permanente..." -ForegroundColor Gray
    foreach ($dep in @(
        "bcrypt", "bcrypt-4.2.1.dist-info",
        "jwt", "PyJWT-2.10.1.dist-info",
        "cryptography", "cryptography-48.0.0.dist-info"
    )) {
        $src = Join-Path $prevLayerStaging $dep
        if (Test-Path $src) {
            Copy-Item -Recurse $src $layerPython
        } else {
            Write-Host "    WARN: dependencia faltante $dep" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "  WARN: no se encontro _staging_shared_layer; el layer ira SIN bcrypt+PyJWT" -ForegroundColor Yellow
    Write-Host "        Para incluirlos, copia _staging_shared_layer\python\{bcrypt,jwt,*.dist-info}" -ForegroundColor Yellow
    Write-Host "        de un layer anterior funcionando." -ForegroundColor Yellow
}

# Incluir firebase-service-account.json en la layer (v07).
# Se busca primero en lambdas/shared/ (committed por Claude en checkpoint)
# y como fallback en Downloads (donde Renzo lo descargo de Firebase).
$saFromLambdas = Join-Path $lambdasDir "shared\firebase-service-account.json"
$saFromDownloads = "C:\Users\marce\OneDrive\Downloads\firebase-service-account.json"
$saSrc = $null
if (Test-Path $saFromLambdas)        { $saSrc = $saFromLambdas }
elseif (Test-Path $saFromDownloads)  { $saSrc = $saFromDownloads }

if ($saSrc) {
    Copy-Item $saSrc -Destination (Join-Path $layerShared "firebase-service-account.json") -Force
    Write-Host "  OK: firebase-service-account.json incluido en layer (origen: $saSrc)" -ForegroundColor Green
} else {
    Write-Host "  ERROR: no se encontro firebase-service-account.json en:" -ForegroundColor Red
    Write-Host "    $saFromLambdas" -ForegroundColor Red
    Write-Host "    $saFromDownloads" -ForegroundColor Red
    Write-Host "  Descargalo desde Firebase Console > Project Settings > Service Accounts" -ForegroundColor Yellow
    exit 1
}

Compress-Archive -Path (Join-Path $layerStaging "python") -DestinationPath $layerZip -Force
$layerSize = [math]::Round((Get-Item $layerZip).Length / 1KB, 1)
Write-Host "  OK: $($layerZip) ($layerSize KB)" -ForegroundColor Green

# ----------------------------------------------------------------------------
# LAMBDA: todos los .py "aplanados" al root del ZIP
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "[2/2] Empaquetando LAMBDA..." -ForegroundColor Gray

$lambdaStaging = Join-Path $stagingDir "lambda"
New-Item -ItemType Directory -Path $lambdaStaging -Force | Out-Null

# Copiar todos los .py de las subcarpetas (admin/, auth/, bodas/, etc.) al staging plano
foreach ($subdir in @("admin", "auth", "bodas", "catalogo", "notifications", "planner")) {
    $subPath = Join-Path $lambdasDir $subdir
    if (Test-Path $subPath) {
        Get-ChildItem $subPath -Filter "*.py" | ForEach-Object {
            Copy-Item $_.FullName $lambdaStaging
        }
    }
}

# Copiar los .py del root de lambdas/ (lambda_function.py, pdf_builder.py)
Get-ChildItem $lambdasDir -Filter "*.py" -File | ForEach-Object {
    Copy-Item $_.FullName $lambdaStaging
}

$count = (Get-ChildItem $lambdaStaging -Filter "*.py").Count
Write-Host "  Total archivos .py en lambda: $count" -ForegroundColor Gray

# Verificacion: lambda_function.py debe existir como entrypoint
if (-not (Test-Path (Join-Path $lambdaStaging "lambda_function.py"))) {
    Write-Host "  ERROR: lambda_function.py no encontrado. Es el handler. Abortando." -ForegroundColor Red
    exit 1
}
Write-Host "  Check: lambda_function.py presente como handler OK" -ForegroundColor Green

Compress-Archive -Path (Join-Path $lambdaStaging "*") -DestinationPath $lambdaZip -Force
$lambdaSize = [math]::Round((Get-Item $lambdaZip).Length / 1KB, 1)
Write-Host "  OK: $($lambdaZip) ($lambdaSize KB)" -ForegroundColor Green

# Limpiar staging
Remove-Item -Recurse -Force $stagingDir

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  EMPAQUETADO COMPLETADO" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  Layer:  $(Split-Path -Leaf $layerZip)" -ForegroundColor Green
Write-Host "  Lambda: $(Split-Path -Leaf $lambdaZip)" -ForegroundColor Green
Write-Host ""
Write-Host "Siguiente paso: .\3_desplegar_aws.ps1" -ForegroundColor Yellow
