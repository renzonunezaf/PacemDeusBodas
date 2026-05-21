# ============================================================================
# 4_verificar.ps1   (v2 - poll de LastUpdateStatus + tests nuevos del modelo)
#
# Health checks post-despliegue. Verifica que:
#   0. La Lambda termino de propagar todos los cambios (poll loop)
#   1. La Lambda esta activa y tiene los layers asociados correctamente
#   2. GET /v1/instrumentos devuelve `incluido_en_paquete_base`
#   3. Login admin funciona
#   4. Cotizacion Lima cercana (Miraflores) -> movilidad = 0 (km < 20)
#   5. Cotizacion Cieneguilla -> movilidad ~230 (sin trafico) o ~270 (con)
#   6. Cotizacion fuera (Lunahuana) -> surge_factor 1.2 + movilidad ~320+
# ============================================================================

$ErrorActionPreference = "Continue"

$region     = "us-east-1"
$lambdaName = "pacem-deus-api"
$apiBase    = "https://57qk0t3z61.execute-api.us-east-1.amazonaws.com/v1"

$adminEmail = "admin@pacemdeus.com"
$adminPwd   = "Welcome.10"

$failed = 0
$passed = 0

function Write-Check {
    param ($Name, $Ok, $Detail = "")
    if ($Ok) {
        Write-Host "  [PASS] $Name" -ForegroundColor Green
        if ($Detail) { Write-Host "         $Detail" -ForegroundColor Gray }
        $script:passed++
    } else {
        Write-Host "  [FAIL] $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "         $Detail" -ForegroundColor Yellow }
        $script:failed++
    }
}

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  Verificacion post-despliegue" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan

# ----------------------------------------------------------------------------
# Step 0: esperar que la Lambda termine de propagar updates
# (resuelve race condition: el deploy puede iniciar 2 updates seguidos,
# el segundo todavia esta InProgress cuando arrancamos verificacion)
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "[0/6] Esperando que la Lambda termine de propagar updates..." -ForegroundColor Gray

$maxWaitSeconds = 60
$elapsed = 0
$step = 3
do {
    $status = aws lambda get-function-configuration `
        --function-name $lambdaName `
        --region $region `
        --query "LastUpdateStatus" `
        --output text 2>&1 | Out-String
    $status = $status.Trim()

    if ($status -eq "Successful") {
        Write-Host "  OK: LastUpdateStatus = Successful (espera total: ${elapsed}s)" -ForegroundColor Green
        break
    }
    if ($status -eq "Failed") {
        Write-Host "  ERROR: LastUpdateStatus = Failed. Revisar CloudWatch /aws/lambda/$lambdaName" -ForegroundColor Red
        exit 1
    }

    Write-Host "  status = $status (${elapsed}s)..." -ForegroundColor DarkGray
    Start-Sleep -Seconds $step
    $elapsed += $step
} while ($elapsed -lt $maxWaitSeconds)

if ($status -ne "Successful") {
    Write-Host "  TIMEOUT despues de ${maxWaitSeconds}s. Estado final: $status" -ForegroundColor Yellow
    Write-Host "  Continuando con los checks de todos modos..." -ForegroundColor Yellow
}

# ----------------------------------------------------------------------------
# Step 1: configuracion de la Lambda
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "[1/6] Estado de la Lambda..." -ForegroundColor Gray

$lambdaConfig = aws lambda get-function-configuration `
    --function-name $lambdaName `
    --region $region `
    --output json 2>&1 | Out-String | ConvertFrom-Json

Write-Check "Lambda state = Active" `
    ($lambdaConfig.State -eq "Active") `
    "State actual: $($lambdaConfig.State)"

Write-Check "LastUpdateStatus = Successful" `
    ($lambdaConfig.LastUpdateStatus -eq "Successful") `
    "LastUpdateStatus actual: $($lambdaConfig.LastUpdateStatus)"

$layersAttached = $lambdaConfig.Layers | ForEach-Object { $_.Arn }
$hasShared = ($layersAttached | Where-Object { $_ -like "*pacem-deus-shared*" }).Count -gt 0
$hasOdbc   = ($layersAttached | Where-Object { $_ -like "*pyodbc313*" }).Count -gt 0

Write-Check "Layer pacem-deus-shared asociado" $hasShared
Write-Check "Layer pyodbc313 asociado"          $hasOdbc

# ----------------------------------------------------------------------------
# Step 2: GET /instrumentos
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "[2/6] GET /v1/instrumentos..." -ForegroundColor Gray

try {
    $instrumentos = Invoke-RestMethod -Uri "$apiBase/instrumentos" -Method GET -TimeoutSec 30
    $piano = $instrumentos.instrumentos | Where-Object { $_.slug -eq "piano" } | Select-Object -First 1
    $vozFem = $instrumentos.instrumentos | Where-Object { $_.slug -eq "voz_femenina" } | Select-Object -First 1

    Write-Check "Total instrumentos > 0" `
        ($instrumentos.instrumentos.Count -gt 0) `
        "Total: $($instrumentos.instrumentos.Count)"

    Write-Check "piano.incluido_en_paquete_base = 1" `
        ($piano -and $piano.incluido_en_paquete_base -eq 1) `
        "valor recibido: $($piano.incluido_en_paquete_base)"

    Write-Check "voz_femenina.incluido_en_paquete_base = 1" `
        ($vozFem -and $vozFem.incluido_en_paquete_base -eq 1) `
        "valor recibido: $($vozFem.incluido_en_paquete_base)"
} catch {
    Write-Check "GET /instrumentos accesible" $false "Error: $_"
}

# ----------------------------------------------------------------------------
# Step 3: login admin
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "[3/6] Login admin..." -ForegroundColor Gray

$token = $null
try {
    $loginBody = @{ email = $adminEmail; password = $adminPwd } | ConvertTo-Json
    $loginResp = Invoke-RestMethod -Uri "$apiBase/auth/login" `
        -Method POST -Body $loginBody -ContentType "application/json" -TimeoutSec 30
    $token = $loginResp.token
    Write-Check "Login admin OK" ($null -ne $token) "Token recibido (len=$($token.Length))"
} catch {
    Write-Check "Login admin OK" $false "Error: $_"
}

# ----------------------------------------------------------------------------
# Step 4: cotizacion Lima cercana (Miraflores, < 20 km)
# Esperado: precio_base = 650, movilidad = 0 (porque km < 20)
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "[4/6] POST /v1/bodas/cotizar (Miraflores, km < 20)..." -ForegroundColor Gray

if ($token) {
    $headers = @{ Authorization = "Bearer $token" }
    $body = @{
        latitud  = -12.121
        longitud = -77.030
        instrumentos = @("piano", "voz_femenina")
    } | ConvertTo-Json

    try {
        $cot = Invoke-RestMethod -Uri "$apiBase/bodas/cotizar" `
            -Method POST -Headers $headers -Body $body -ContentType "application/json" -TimeoutSec 30

        Write-Check "Miraflores: precio_base = 650.00" `
            ($cot.precio_base -eq 650.00) `
            "precio_base = $($cot.precio_base), distancia = $($cot.distancia_km) km"

        Write-Check "Miraflores: movilidad = 0 (km < 20)" `
            ($cot.precio_movilidad -eq 0) `
            "precio_movilidad = $($cot.precio_movilidad)"

        Write-Check "Miraflores: surge_factor = 1.0 (dentro Lima)" `
            ($cot.surge_factor -eq 1.0) `
            "surge_factor = $($cot.surge_factor)"
    } catch {
        Write-Check "Cotizacion Miraflores accesible" $false "Error: $_"
    }
}

# ----------------------------------------------------------------------------
# Step 5: cotizacion Cieneguilla (~35 km, fuera del radio libre pero
# dentro de Lima Metropolitana segun la API)
# Esperado: movilidad por distancia ~230, + posible recargo trafico
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "[5/6] POST /v1/bodas/cotizar (Cieneguilla, ~35 km)..." -ForegroundColor Gray

if ($token) {
    $headers = @{ Authorization = "Bearer $token" }
    $body = @{
        latitud  = -12.121
        longitud = -76.793
        instrumentos = @("piano", "voz_femenina")
    } | ConvertTo-Json

    try {
        $cot = Invoke-RestMethod -Uri "$apiBase/bodas/cotizar" `
            -Method POST -Headers $headers -Body $body -ContentType "application/json" -TimeoutSec 30

        Write-Check "Cieneguilla: movilidad > 0 (km > 20)" `
            ($cot.precio_movilidad -gt 0) `
            "precio_movilidad = $($cot.precio_movilidad), distancia = $($cot.distancia_km) km"

        # En el rango 30-40 km, movilidad por km esta entre ~200 y ~270
        Write-Check "Cieneguilla: movilidad en rango razonable (200-400)" `
            ($cot.precio_movilidad -ge 200 -and $cot.precio_movilidad -le 400) `
            "precio_movilidad = $($cot.precio_movilidad)"
    } catch {
        Write-Check "Cotizacion Cieneguilla accesible" $false "Error: $_"
    }
}

# ----------------------------------------------------------------------------
# Step 6: cotizacion fuera de Lima (Lunahuana, ~200 km)
# Esperado: surge_factor = 1.2, movilidad >= 320 (plateau + posible trafico)
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "[6/6] POST /v1/bodas/cotizar (Lunahuana, ~200 km)..." -ForegroundColor Gray

if ($token) {
    $headers = @{ Authorization = "Bearer $token" }
    $body = @{
        latitud  = -12.96
        longitud = -76.14
        instrumentos = @("piano", "voz_femenina")
    } | ConvertTo-Json

    try {
        $cot = Invoke-RestMethod -Uri "$apiBase/bodas/cotizar" `
            -Method POST -Headers $headers -Body $body -ContentType "application/json" -TimeoutSec 30

        Write-Check "Lunahuana: fuera_de_lima = true" `
            ($cot.fuera_de_lima -eq $true) `
            "distancia = $($cot.distancia_km) km"

        Write-Check "Lunahuana: surge_factor = 1.2 (plateau fuera Lima)" `
            ($cot.surge_factor -eq 1.2) `
            "surge_factor = $($cot.surge_factor)"

        Write-Check "Lunahuana: precio_base = 780 (650 x 1.2)" `
            ($cot.precio_base -eq 780.0) `
            "precio_base = $($cot.precio_base)"

        Write-Check "Lunahuana: movilidad >= 320 (plateau + posible trafico)" `
            ($cot.precio_movilidad -ge 320) `
            "precio_movilidad = $($cot.precio_movilidad)"
    } catch {
        Write-Check "Cotizacion Lunahuana accesible" $false "Error: $_"
    }
}

# ----------------------------------------------------------------------------
# Resumen final
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  RESUMEN" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  Passed: $passed" -ForegroundColor Green
if ($failed -gt 0) {
    Write-Host "  Failed: $failed" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Hubo fallos. Revisar CloudWatch /aws/lambda/$lambdaName" -ForegroundColor Yellow
    exit 1
} else {
    Write-Host ""
    Write-Host "  TODO OK - el checkpoint v02 esta correctamente desplegado." -ForegroundColor Green
}
