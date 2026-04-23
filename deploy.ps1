# ═══════════════════════════════════════════════════════════════
# Pacem Deus Bodas — Script de despliegue local
# IS276 — Plataformas Móviles y Análisis Cloud — Grupo 2
# ═══════════════════════════════════════════════════════════════
# Uso:
#   cd C:\AMD\Pacem_Deus_Android
#   powershell -ExecutionPolicy Bypass -File deploy.ps1
#
# Requisitos previos:
#   - PostgreSQL 18 instalado
#   - Python 3.10+ instalado (py launcher)
#   - Dependencias instaladas: pip install -r pacem-deus-api\requirements.txt
#   - Archivo pacem-deus-api\.env creado a partir de .env.example
# ═══════════════════════════════════════════════════════════════

# ─── Rutas de herramientas locales (pueden variar por máquina) ──
$PSQL    = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
$ADB     = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$EnvFile = "$PSScriptRoot\pacem-deus-api\.env"

Write-Host "`n  Pacem Deus Bodas - Deploy Local`n" -ForegroundColor Cyan

# ─── Cargar variables desde .env ──────────────────────────────
# PowerShell no parsea .env nativamente: recorremos el archivo línea a línea
# e inyectamos cada par KEY=VALUE como variable de entorno del proceso actual.
if (-not (Test-Path $EnvFile)) {
    Write-Host "ERROR: No se encontró $EnvFile" -ForegroundColor Red
    Write-Host "       Copie pacem-deus-api\.env.example como pacem-deus-api\.env" -ForegroundColor Yellow
    Write-Host "       y complete sus credenciales locales antes de ejecutar." -ForegroundColor Yellow
    exit 1
}

Get-Content $EnvFile | ForEach-Object {
    # Ignorar comentarios y líneas vacías
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line.Split("=", 2)
        $key   = $parts[0].Trim()
        $value = $parts[1].Trim()
        Set-Item -Path "Env:$key" -Value $value
    }
}

# psql usa la variable PGPASSWORD (no DB_PASSWORD)
$env:PGPASSWORD = $env:DB_PASSWORD

# 1. Matar procesos
Write-Host "[1/4] Cerrando procesos..." -ForegroundColor Yellow
Get-Process -Name "python","py","java" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep 2
Write-Host "      OK" -ForegroundColor Green

# 2. PostgreSQL
Write-Host "[2/4] PostgreSQL..." -ForegroundColor Yellow
$svc = Get-Service "postgresql-x64-18" -ErrorAction SilentlyContinue
if ($svc -and $svc.Status -ne "Running") { net start postgresql-x64-18 2>$null; Start-Sleep 3 }
& $PSQL -U $env:DB_USER -d $env:DB_NAME -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;" 2>$null
& $PSQL -U $env:DB_USER -d $env:DB_NAME -f "$PSScriptRoot\pacem-deus-api\sql\schema.sql" 2>$null | Out-Null
Write-Host "      Schema OK" -ForegroundColor Green

# 3. Seed
Write-Host "[3/4] Seed..." -ForegroundColor Yellow
Push-Location "$PSScriptRoot\pacem-deus-api"
py sql/seed.py 2>$null
Pop-Location
Write-Host "      Seed OK" -ForegroundColor Green

# 4. Limpiar emulador
Write-Host "[4/4] Limpiando emulador..." -ForegroundColor Yellow
& $ADB shell pm clear com.pacemdeus.bodas 2>$null | Out-Null
Write-Host "      OK`n" -ForegroundColor Green

# Levantar server
Write-Host "  Backend: http://localhost:5000" -ForegroundColor Cyan
Write-Host "  Login de prueba: novia1@correo.com / PacemDeus2026!`n" -ForegroundColor White
Set-Location "$PSScriptRoot\pacem-deus-api"
py server.py
