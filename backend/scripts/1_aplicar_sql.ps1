# ============================================================================
# 1_aplicar_sql.ps1   (v3 - SqlClient nativo + lista v02)
#
# Aplica las migraciones SQL pendientes a la BD pacem_deus_bodas en RDS.
#
# Usa System.Data.SqlClient (incluido en Windows PowerShell 5.1+ via
# .NET Framework). NO requiere instalar sqlcmd ni el modulo SqlServer.
# Soporta multiples batches separados por 'GO' y captura los PRINTs del
# SQL Server para mostrar progreso de cada migracion.
#
# Idempotencia: cada migracion usa IF NOT EXISTS / CREATE OR ALTER, asi
# que correr este script varias veces no rompe nada.
# ============================================================================

$ErrorActionPreference = "Stop"

# Cwd neutral para evitar "in use" si la sesion quedo dentro de una carpeta
# que pudiera tocarse mas adelante
Set-Location ([Environment]::GetFolderPath('UserProfile'))

Add-Type -AssemblyName "System.Data" -ErrorAction SilentlyContinue

$server   = if ($env:PACEM_RDS_HOST) { "$($env:PACEM_RDS_HOST),1433" }
             else { "pacem-deus-db.cej4islmfw0v.us-east-1.rds.amazonaws.com,1433" }
$database = if ($env:PACEM_RDS_DB)   { $env:PACEM_RDS_DB }   else { "pacem_deus_bodas" }
$user     = if ($env:PACEM_RDS_USER) { $env:PACEM_RDS_USER } else { "admin" }
# Acepta PACEM_RDS_PWD (convencion del proyecto, cargado via profile shim
# desde el registry de Windows) o DB_PWD (variable ad-hoc).
$password = if ($env:PACEM_RDS_PWD) { $env:PACEM_RDS_PWD } else { $env:DB_PWD }
if ([string]::IsNullOrWhiteSpace($password)) {
    Write-Host "ERROR: Falta variable de entorno PACEM_RDS_PWD o DB_PWD" -ForegroundColor Red
    Write-Host "  El profile shim debe cargar PACEM_RDS_PWD automaticamente." -ForegroundColor Yellow
    Write-Host "  Si corres con -NoProfile, define DB_PWD manualmente antes." -ForegroundColor Yellow
    exit 1
}

$sqlDir = Join-Path (Split-Path -Parent $PSScriptRoot) "infra\sql"

if (-not (Test-Path $sqlDir)) {
    Write-Host "ERROR: No se encontro $sqlDir" -ForegroundColor Red
    exit 1
}

$connStr = "Server=$server;Database=$database;User Id=$user;Password=$password;" +
           "TrustServerCertificate=True;Encrypt=True;Connection Timeout=30;Pooling=False"

# Lista de migraciones a aplicar en orden cronologico
$scripts = @(
    "m04_planners_seed.sql",
    "m05_fotos_caption_autor.sql",
    "m07_sps_aux_obtener.sql",
    "m08_disponibilidad.sql",
    "m09_marker_seed.sql",
    "m10_anotaciones_xl.sql",
    "m11_fixes_checkpoint_v01.sql",
    "m12_movilidad_v2.sql",
    "m13_limpiar_planners_duplicados.sql",
    "m14_notificaciones.sql",
    "m15_fcm_tokens.sql",
    "m16_usp_usuario_obtener.sql",
    "m17_mapa_incluir_borradores.sql",
    "m18_disponibilidad_v2.sql"
)

# ----------------------------------------------------------------------------
# Funcion: ejecuta un .sql con multiples batches separados por GO
# ----------------------------------------------------------------------------
function Invoke-SqlScript {
    param(
        [string]$ScriptPath,
        [string]$ConnectionString
    )

    $sql = Get-Content $ScriptPath -Raw -Encoding UTF8

    # Separar batches por 'GO' en linea propia
    $batches = $sql -split '(?im)^\s*GO\s*$'

    $conn = New-Object System.Data.SqlClient.SqlConnection($ConnectionString)

    # Capturar PRINT statements del SQL Server
    $msgBuffer = New-Object System.Collections.ArrayList
    $handler = [System.Data.SqlClient.SqlInfoMessageEventHandler] {
        param($eventSender, $eventArgs)
        [void]$msgBuffer.Add($eventArgs.Message)
    }.GetNewClosure()
    $conn.add_InfoMessage($handler)

    $conn.Open()

    try {
        foreach ($batch in $batches) {
            $trimmed = $batch.Trim()
            if ($trimmed.Length -eq 0) { continue }
            # Saltar batches que solo tienen comentarios
            $noComments = $trimmed -replace '(?ms)/\*.*?\*/', '' -replace '(?m)^\s*--[^\r\n]*', ''
            if ($noComments.Trim().Length -eq 0) { continue }

            $cmd = $conn.CreateCommand()
            $cmd.CommandText = $trimmed
            $cmd.CommandTimeout = 300
            try {
                $cmd.ExecuteNonQuery() | Out-Null
            } catch {
                Write-Host ""
                Write-Host "  ERROR en batch:" -ForegroundColor Red
                $preview = if ($trimmed.Length -gt 200) { $trimmed.Substring(0, 200) + "..." } else { $trimmed }
                Write-Host "  $preview" -ForegroundColor Yellow
                Write-Host "  Excepcion: $($_.Exception.Message)" -ForegroundColor Red
                throw
            } finally {
                $cmd.Dispose()
            }
        }
    } finally {
        $conn.Close()
        $conn.Dispose()
    }

    return $msgBuffer
}

# ----------------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  Aplicando migraciones SQL a $database" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  Server: $server"
Write-Host "  Total migraciones: $($scripts.Count)"
Write-Host "  Cliente: System.Data.SqlClient (PowerShell built-in)"
Write-Host ""

$applied = 0
$skipped = 0

foreach ($scriptName in $scripts) {
    $path = Join-Path $sqlDir $scriptName
    if (-not (Test-Path $path)) {
        Write-Host "  [SKIP] $scriptName  (no existe)" -ForegroundColor Yellow
        $skipped++
        continue
    }

    Write-Host "  Aplicando $scriptName ..." -ForegroundColor Gray -NoNewline

    try {
        $msgs = Invoke-SqlScript -ScriptPath $path -ConnectionString $connStr
        Write-Host " OK" -ForegroundColor Green
        foreach ($m in $msgs) {
            $clean = "$m".Trim()
            if ($clean.Length -gt 0) {
                Write-Host "      $clean" -ForegroundColor DarkGray
            }
        }
        $applied++
    } catch {
        Write-Host " FAILED" -ForegroundColor Red
        Write-Host ""
        Write-Host "Migracion fallida. Abortando." -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  RESUMEN" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "  Aplicadas:  $applied" -ForegroundColor Green
if ($skipped -gt 0) {
    Write-Host "  Salteadas:  $skipped" -ForegroundColor Yellow
}

# ----------------------------------------------------------------------------
# Verificacion: estado final de las cosas que importan
# ----------------------------------------------------------------------------
Write-Host ""
Write-Host "Verificacion del estado final de la BD..." -ForegroundColor Gray

$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$conn.Open()
try {
    # Check 1: valores de pricing base
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = "SELECT precio_paquete_base, precio_instrumento_adicional, " +
                       "surge_fuera_lima_factor_max, mov_distancia_libre_km, " +
                       "mov_arranque_movilidad, mov_tope_movilidad, " +
                       "mov_curva_exponente, mov_traffic_umbral_min, " +
                       "mov_traffic_tarifa_minuto " +
                       "FROM configuracion_precios"
    $reader = $cmd.ExecuteReader()
    if ($reader.Read()) {
        Write-Host "  precio_paquete_base          = $($reader['precio_paquete_base'])"           -ForegroundColor Green
        Write-Host "  precio_instrumento_adicional = $($reader['precio_instrumento_adicional'])"  -ForegroundColor Green
        Write-Host "  surge_fuera_lima_factor_max  = $($reader['surge_fuera_lima_factor_max'])"   -ForegroundColor Green
        Write-Host "  mov_distancia_libre_km       = $($reader['mov_distancia_libre_km'])"        -ForegroundColor Green
        Write-Host "  mov_arranque_movilidad       = $($reader['mov_arranque_movilidad'])"        -ForegroundColor Green
        Write-Host "  mov_tope_movilidad           = $($reader['mov_tope_movilidad'])"            -ForegroundColor Green
        Write-Host "  mov_curva_exponente          = $($reader['mov_curva_exponente'])"           -ForegroundColor Green
        Write-Host "  mov_traffic_umbral_min       = $($reader['mov_traffic_umbral_min'])"        -ForegroundColor Green
        Write-Host "  mov_traffic_tarifa_minuto    = $($reader['mov_traffic_tarifa_minuto'])"     -ForegroundColor Green
    }
    $reader.Close()
    $cmd.Dispose()

    # Check 2: SP usp_instrumentos_listar incluye incluido_en_paquete_base?
    # (consulta el cuerpo del SP, no sys.columns que es para tablas)
    $cmd2 = $conn.CreateCommand()
    $cmd2.CommandText = "SELECT CASE WHEN sm.definition LIKE '%incluido_en_paquete_base%' THEN 1 ELSE 0 END " +
                        "FROM sys.procedures sp " +
                        "JOIN sys.sql_modules sm ON sm.object_id = sp.object_id " +
                        "WHERE sp.name = 'usp_instrumentos_listar'"
    $hasColumn = $cmd2.ExecuteScalar()
    $cmd2.Dispose()

    if ($hasColumn -eq 1) {
        Write-Host "  usp_instrumentos_listar incluye incluido_en_paquete_base: SI" -ForegroundColor Green
    } else {
        Write-Host "  usp_instrumentos_listar incluye incluido_en_paquete_base: NO" -ForegroundColor Red
    }
} finally {
    $conn.Close()
    $conn.Dispose()
}

Write-Host ""
Write-Host "SQL migraciones aplicadas. Siguiente paso: .\2_empaquetar.ps1" -ForegroundColor Green
