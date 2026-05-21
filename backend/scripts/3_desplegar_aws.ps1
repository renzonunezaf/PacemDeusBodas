# ============================================================================
# 3_desplegar_aws.ps1
#
# Sube a AWS los 2 ZIPs mas recientes en dist/:
#   1. Publica una version nueva del layer pacem-deus-shared
#   2. Sube el codigo nuevo a la Lambda pacem-deus-api
#   3. Reconfigura la Lambda para usar la version nueva del layer
#      (manteniendo el layer pyodbc313 que viene de antes)
#
# IMPORTANTE: aws.exe escribe warnings inocuos a stderr (ej. cuando se
# usa --no-paginate). Con $ErrorActionPreference = "Stop" eso aborta
# PowerShell aunque el comando haya sido exitoso. Por eso este script
# usa "Continue" y un helper Invoke-Aws que redirige stderr a archivo
# temporal y chequea el exit code explicitamente.
#
# Requiere:
#   - AWS CLI configurado (aws sts get-caller-identity debe responder)
#   - Permisos: lambda:PublishLayerVersion, lambda:UpdateFunctionCode,
#               lambda:UpdateFunctionConfiguration, lambda:GetFunction
# ============================================================================

$ErrorActionPreference = "Continue"

$region        = "us-east-1"
$lambdaName    = "pacem-deus-api"
$layerName     = "pacem-deus-shared"
$layerOdbcName = "pyodbc313"

$root    = Split-Path -Parent $PSScriptRoot
$distDir = Join-Path $root "dist"

if (-not (Test-Path $distDir)) {
    Write-Host "ERROR: no se encuentra $distDir" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  Desplegando a AWS" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan

function Invoke-Aws {
    param(
        [Parameter(Mandatory)][string]$Description,
        [Parameter(Mandatory)][string[]]$Arguments
    )
    $tmpErr = [System.IO.Path]::GetTempFileName()
    try {
        $stdout = & aws @Arguments 2>$tmpErr
        $exit = $LASTEXITCODE
        if ($exit -ne 0) {
            Write-Host ""
            Write-Host "ERROR en: $Description" -ForegroundColor Red
            Write-Host "Exit code: $exit" -ForegroundColor Red
            if (Test-Path $tmpErr) {
                Write-Host "STDERR:" -ForegroundColor Red
                Get-Content $tmpErr | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
            }
            throw "Comando AWS fallo (exit $exit)"
        }
        return ($stdout -join "`n")
    }
    finally {
        if (Test-Path $tmpErr) { Remove-Item $tmpErr -Force -ErrorAction SilentlyContinue }
    }
}

Write-Host ""
Write-Host "[0/4] Verificando credenciales AWS..." -ForegroundColor Gray
$identityJson = Invoke-Aws -Description "sts get-caller-identity" -Arguments @(
    "sts", "get-caller-identity", "--output", "json"
)
$identity = $identityJson | ConvertFrom-Json
Write-Host "  Account: $($identity.Account)" -ForegroundColor Green

Write-Host ""
Write-Host "[1/4] Localizando ZIPs mas recientes..." -ForegroundColor Gray
$layerZip = Get-ChildItem $distDir -Filter "pacem-deus-shared-layer-v*.zip" |
            Sort-Object {
                if ($_.Name -match "v(\d+)\.zip$") { [int]$Matches[1] } else { 0 }
            } -Descending | Select-Object -First 1
$lambdaZip = Get-ChildItem $distDir -Filter "pacem-deus-api-v*.zip" |
             Sort-Object {
                if ($_.Name -match "v(\d+)\.zip$") { [int]$Matches[1] } else { 0 }
             } -Descending | Select-Object -First 1
if (-not $layerZip)  { throw "No se encontro layer ZIP en $distDir" }
if (-not $lambdaZip) { throw "No se encontro lambda ZIP en $distDir" }
Write-Host "  Layer:  $($layerZip.Name)" -ForegroundColor Green
Write-Host "  Lambda: $($lambdaZip.Name)" -ForegroundColor Green

Write-Host ""
Write-Host "[2/4] Publicando layer $layerName..." -ForegroundColor Gray
$publishJsonStr = Invoke-Aws -Description "publish-layer-version" -Arguments @(
    "lambda", "publish-layer-version",
    "--layer-name", $layerName,
    "--description", "Pacem Deus Bodas - shared layer",
    "--zip-file", "fileb://$($layerZip.FullName)",
    "--compatible-runtimes", "python3.13",
    "--region", $region,
    "--output", "json"
)
$publishJson = $publishJsonStr | ConvertFrom-Json
$newLayerArn = $publishJson.LayerVersionArn
$newLayerVersion = $publishJson.Version
Write-Host "  OK: layer publicado, version $newLayerVersion" -ForegroundColor Green

Write-Host ""
Write-Host "[3/4] Obteniendo ARN actual de $layerOdbcName..." -ForegroundColor Gray
$odbcArn = (Invoke-Aws -Description "list-layer-versions pyodbc313" -Arguments @(
    "lambda", "list-layer-versions",
    "--layer-name", $layerOdbcName,
    "--region", $region,
    "--query", "LayerVersions[0].LayerVersionArn",
    "--output", "text"
)).Trim()
if ([string]::IsNullOrEmpty($odbcArn) -or $odbcArn -eq "None") {
    throw "No se encontro layer $layerOdbcName en AWS"
}
Write-Host "  OK: pyodbc layer = $odbcArn" -ForegroundColor Green

Write-Host ""
Write-Host "[4/4] Actualizando Lambda $lambdaName..." -ForegroundColor Gray
Write-Host "  Subiendo codigo..." -ForegroundColor Gray
Invoke-Aws -Description "update-function-code" -Arguments @(
    "lambda", "update-function-code",
    "--function-name", $lambdaName,
    "--zip-file", "fileb://$($lambdaZip.FullName)",
    "--region", $region,
    "--output", "json"
) | Out-Null
Write-Host "  OK: codigo subido" -ForegroundColor Green

Write-Host "  Esperando que Lambda termine de actualizar codigo..." -ForegroundColor Gray
$intentos = 0
do {
    Start-Sleep -Seconds 2
    $status = (Invoke-Aws -Description "get-function-configuration (post code)" -Arguments @(
        "lambda", "get-function-configuration",
        "--function-name", $lambdaName,
        "--region", $region,
        "--query", "LastUpdateStatus",
        "--output", "text"
    )).Trim()
    $intentos++
    if ($intentos -gt 30) { throw "Timeout esperando code update (>60s)" }
} while ($status -eq "InProgress")
if ($status -ne "Successful") {
    throw "LastUpdateStatus = $status (esperado: Successful)"
}
Write-Host "  OK: codigo activo" -ForegroundColor Green

Write-Host "  Asociando layer nuevo..." -ForegroundColor Gray
Invoke-Aws -Description "update-function-configuration (layers)" -Arguments @(
    "lambda", "update-function-configuration",
    "--function-name", $lambdaName,
    "--layers", $newLayerArn, $odbcArn,
    "--region", $region,
    "--output", "json"
) | Out-Null
Write-Host "  OK: layer v$newLayerVersion asociado a la Lambda" -ForegroundColor Green

Write-Host "  Esperando que la reconfiguracion termine..." -ForegroundColor Gray
$intentos = 0
do {
    Start-Sleep -Seconds 2
    $status = (Invoke-Aws -Description "get-function-configuration (post reconfig)" -Arguments @(
        "lambda", "get-function-configuration",
        "--function-name", $lambdaName,
        "--region", $region,
        "--query", "LastUpdateStatus",
        "--output", "text"
    )).Trim()
    $intentos++
    if ($intentos -gt 30) { throw "Timeout esperando reconfig (>60s)" }
} while ($status -eq "InProgress")

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Green
Write-Host "  DESPLIEGUE COMPLETADO" -ForegroundColor Green
Write-Host "===========================================================" -ForegroundColor Green
Write-Host "  Layer:  $layerName version $newLayerVersion" -ForegroundColor Green
Write-Host "  Lambda: $lambdaName (codigo + layer asociado)" -ForegroundColor Green
Write-Host ""
Write-Host "Siguiente paso: .\4_verificar.ps1" -ForegroundColor Yellow
