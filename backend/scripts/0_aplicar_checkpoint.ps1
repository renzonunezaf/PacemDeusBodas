# ============================================================================
# 0_aplicar_checkpoint.ps1   (v3 - fix LASTEXITCODE heredado)
#
# Script maestro: orquesta los 4 pasos del checkpoint completo.
#
# FIX v3: los sub-scripts son todos cmdlets de .NET (no .exe), asi que
# nunca setean $LASTEXITCODE explicitamente. Si la sesion PowerShell
# tiene $LASTEXITCODE != 0 heredado de una corrida anterior con `exit 1`,
# el master concluia falsamente que el sub-script fallo. Solucion:
# resetear $LASTEXITCODE antes de cada `&` y envolver en try/catch.
#
# Pasos:
#   1. Aplicar migraciones SQL via SqlClient nativo
#   2. Empaquetar Layer + Lambda en dist/
#   3. Desplegar a AWS
#   4. Verificacion end-to-end via API REST
#
# Es seguro re-correr (todos los sub-scripts son idempotentes).
# ============================================================================

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "###########################################################" -ForegroundColor Cyan
Write-Host "##                                                       ##" -ForegroundColor Cyan
Write-Host "##  CHECKPOINT v03 - Aplicacion completa                ##" -ForegroundColor Cyan
Write-Host "##                                                       ##" -ForegroundColor Cyan
Write-Host "###########################################################" -ForegroundColor Cyan

# ----------------------------------------------------------------------------
# Pre-flight: verificar integridad del checkpoint
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "Pre-flight: verificando integridad del checkpoint..." -ForegroundColor Gray

$apiRoot = Split-Path -Parent $PSScriptRoot

$archivosClave = @(
    "lambdas\shared\db.py",
    "lambdas\shared\pricing.py",
    "lambdas\shared\auth.py",
    "lambdas\shared\distance.py",
    "lambdas\lambda_function.py",
    "lambdas\pdf_builder.py",
    "infra\sql\m11_fixes_checkpoint_v01.sql",
    "infra\sql\m12_movilidad_v2.sql",
    "infra\sql\m13_limpiar_planners_duplicados.sql",
    "dist\_staging_shared_layer\python\bcrypt",
    "dist\_staging_shared_layer\python\jwt"
)

$missing = @()
foreach ($rel in $archivosClave) {
    $full = Join-Path $apiRoot $rel
    if (-not (Test-Path $full)) {
        $missing += $rel
    }
}

if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "ERROR: el checkpoint esta incompleto. Faltan archivos clave:" -ForegroundColor Red
    foreach ($m in $missing) {
        Write-Host "  $m" -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "Esto suele indicar una descarga truncada del ZIP." -ForegroundColor Yellow
    Write-Host "Re-descargar el ZIP completo (pesa ~700-800 KB)." -ForegroundColor Yellow
    exit 1
}

# Validacion adicional: pricing.py debe tener las funciones del modelo v02+
$pricingPath = Join-Path $apiRoot "lambdas\shared\pricing.py"
$pricingContent = Get-Content $pricingPath -Raw
if ($pricingContent -notmatch "calculate_mobility_distance" -or
    $pricingContent -notmatch "calculate_traffic_surcharge") {
    Write-Host ""
    Write-Host "ERROR: pricing.py no contiene las funciones del modelo v02+." -ForegroundColor Red
    exit 1
}

Write-Host "OK: $($archivosClave.Count) archivos clave verificados" -ForegroundColor Green

# ----------------------------------------------------------------------------
# Orquestacion de los 4 pasos
# Fix v3: resetear $LASTEXITCODE y envolver en try/catch para detectar fallos
# por excepcion en lugar de codigo de salida (que no se setea en cmdlets puros)
# ----------------------------------------------------------------------------
$steps = @(
    @{ Name = "Aplicar migraciones SQL";  Script = "1_aplicar_sql.ps1"   },
    @{ Name = "Empaquetar Layer + Lambda"; Script = "2_empaquetar.ps1"    },
    @{ Name = "Desplegar a AWS";           Script = "3_desplegar_aws.ps1" },
    @{ Name = "Verificacion post-deploy";  Script = "4_verificar.ps1"     }
)

$start = Get-Date

foreach ($step in $steps) {
    $scriptPath = Join-Path $PSScriptRoot $step.Script
    if (-not (Test-Path $scriptPath)) {
        Write-Host ""
        Write-Host "ERROR: no se encontro $scriptPath" -ForegroundColor Red
        exit 1
    }

    Write-Host ""
    Write-Host "###########################################################" -ForegroundColor Magenta
    Write-Host "##  Paso: $($step.Name)" -ForegroundColor Magenta
    Write-Host "###########################################################" -ForegroundColor Magenta

    # Reset critico: $LASTEXITCODE puede traer un valor heredado de una
    # corrida anterior con exit != 0 (sub-scripts son cmdlets .NET puros
    # y NUNCA reescriben este valor). Sin este reset, el master concluye
    # falsamente que el sub-script fallo.
    $global:LASTEXITCODE = 0

    try {
        & $scriptPath
        # Si el sub-script si hizo `exit 1` o llamo a un .exe que fallo,
        # $LASTEXITCODE refleja el error real.
        if ($LASTEXITCODE -ne 0) {
            throw "Sub-script terminado con exit code $LASTEXITCODE"
        }
    } catch {
        Write-Host ""
        Write-Host "PASO FALLIDO: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Abortando." -ForegroundColor Red
        exit 1
    }
}

$elapsed = (Get-Date) - $start
$mins = [math]::Floor($elapsed.TotalMinutes)
$secs = [math]::Floor($elapsed.Seconds)

Write-Host ""
Write-Host "###########################################################" -ForegroundColor Green
Write-Host "##                                                       ##" -ForegroundColor Green
Write-Host "##  CHECKPOINT v03 APLICADO CON EXITO                  ##" -ForegroundColor Green
Write-Host "##  Tiempo total: ${mins}m ${secs}s                              ##" -ForegroundColor Green
Write-Host "##                                                       ##" -ForegroundColor Green
Write-Host "###########################################################" -ForegroundColor Green
