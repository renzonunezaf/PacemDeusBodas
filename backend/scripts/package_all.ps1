# =============================================================================
# package_all.ps1
# Empaqueta TODAS las Lambdas en un solo paso.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File .\scripts\package_all.ps1
#
# Genera un zip por cada handler en lambdas\, en la carpeta dist\
# Excluye la carpeta shared\ porque va en una layer separada.
# =============================================================================

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$lambdasFolder = Join-Path $projectRoot "lambdas"
$distFolder = Join-Path $projectRoot "dist"

if (-not (Test-Path $distFolder)) {
    New-Item -ItemType Directory -Path $distFolder | Out-Null
}

# Buscar todos los .py excepto los de la carpeta shared (esos van en la layer)
$lambdaFiles = Get-ChildItem -Path $lambdasFolder -Recurse -Filter "*.py" |
    Where-Object {
        $_.FullName -notmatch "\\shared\\" -and $_.Name -ne "__init__.py"
    }

Write-Host "Encontrados $($lambdaFiles.Count) lambdas para empaquetar" -ForegroundColor Cyan
Write-Host ""

$exitosos = 0
$fallidos = 0
$packageScript = Join-Path $PSScriptRoot "package_lambda.ps1"

foreach ($lambda in $lambdaFiles) {
    $relativePath = $lambda.FullName.Substring($projectRoot.Length + 1)
    Write-Host "Procesando: $relativePath" -ForegroundColor White
    try {
        & $packageScript -LambdaPath $relativePath
        $exitosos++
    } catch {
        Write-Host "FALLO: $relativePath - $_" -ForegroundColor Red
        $fallidos++
    }
}

Write-Host ""
Write-Host "Resumen: $exitosos exitosos, $fallidos fallidos" -ForegroundColor Cyan
Write-Host "Archivos en: $distFolder" -ForegroundColor Cyan

Get-ChildItem $distFolder -Filter "*.zip" |
    Select-Object Name, @{N='KB';E={[math]::Round($_.Length/1KB, 1)}} |
    Format-Table
