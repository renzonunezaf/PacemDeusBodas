# =============================================================================
# package_lambda.ps1
# Empaqueta un Lambda individual en un .zip listo para subir a AWS Console.
#
# Uso (con bypass de Execution Policy):
#   powershell -ExecutionPolicy Bypass -File .\scripts\package_lambda.ps1 `
#       -LambdaPath "lambdas\auth\post_auth_login.py"
#
# O dentro de PowerShell:
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#   .\scripts\package_lambda.ps1 -LambdaPath "lambdas\auth\post_auth_login.py"
#
# Genera:
#   dist\<nombre_handler>.zip  con el archivo renombrado a lambda_function.py
# =============================================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$LambdaPath
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$lambdaFullPath = Join-Path $projectRoot $LambdaPath

if (-not (Test-Path $lambdaFullPath)) {
    Write-Host "ERROR: No se encontro $lambdaFullPath" -ForegroundColor Red
    exit 1
}

$lambdaFileName = [System.IO.Path]::GetFileNameWithoutExtension($lambdaFullPath)

$distFolder = Join-Path $projectRoot "dist"
if (-not (Test-Path $distFolder)) {
    New-Item -ItemType Directory -Path $distFolder | Out-Null
}

$stagingFolder = Join-Path $distFolder "_staging_$lambdaFileName"
if (Test-Path $stagingFolder) {
    Remove-Item -Recurse -Force $stagingFolder
}
New-Item -ItemType Directory -Path $stagingFolder | Out-Null

# Convencion AWS: el handler se llama lambda_function.py
Copy-Item -Path $lambdaFullPath -Destination (Join-Path $stagingFolder "lambda_function.py")

$zipPath = Join-Path $distFolder "$lambdaFileName.zip"
if (Test-Path $zipPath) {
    Remove-Item -Force $zipPath
}

Compress-Archive -Path "$stagingFolder\*" -DestinationPath $zipPath -CompressionLevel Optimal

Remove-Item -Recurse -Force $stagingFolder

$size = (Get-Item $zipPath).Length / 1KB
Write-Host "OK $LambdaPath -> $zipPath ($([math]::Round($size, 1)) KB)" -ForegroundColor Green
