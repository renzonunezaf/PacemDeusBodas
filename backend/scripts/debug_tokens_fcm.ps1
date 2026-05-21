# debug_tokens_fcm.ps1
# Diagnostico tokens FCM en la BD: cuantos admins tienen token registrado,
# cuantos lo tienen NULL, ultima actualizacion, etc.

$ErrorActionPreference = "Continue"

$server = $env:PACEM_RDS_HOST
$user   = $env:PACEM_RDS_USER
$pwd    = $env:PACEM_RDS_PWD
$db     = $env:PACEM_RDS_DB

if (-not $server -or -not $user -or -not $pwd -or -not $db) {
    Write-Host "ERROR: variables PACEM_RDS_* no estan cargadas." -ForegroundColor Red
    Write-Host "Revisa el shim del profile." -ForegroundColor Yellow
    exit 1
}

$connStr = "Server=$server,1433;Database=$db;User Id=$user;Password=$pwd;Encrypt=True;TrustServerCertificate=True;"
$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$conn.Open()

Write-Host ""
Write-Host "=== Tokens FCM por rol ===" -ForegroundColor Cyan
$cmd = $conn.CreateCommand()
$cmd.CommandText = @"
SELECT  rol,
        COUNT(*)                                    AS total_usuarios,
        SUM(CASE WHEN fcm_token IS NULL THEN 1 ELSE 0 END) AS sin_token,
        SUM(CASE WHEN fcm_token IS NOT NULL THEN 1 ELSE 0 END) AS con_token
FROM    usuario
WHERE   activo = 1
GROUP BY rol
ORDER BY rol
"@
$rdr = $cmd.ExecuteReader()
"{0,-20} {1,15} {2,12} {3,12}" -f "Rol","Total","Sin token","Con token"
"{0,-20} {1,15} {2,12} {3,12}" -f ("-" * 20),("-" * 15),("-" * 12),("-" * 12)
while ($rdr.Read()) {
    "{0,-20} {1,15} {2,12} {3,12}" -f $rdr["rol"], $rdr["total_usuarios"], $rdr["sin_token"], $rdr["con_token"]
}
$rdr.Close()

Write-Host ""
Write-Host "=== Detalle admins ===" -ForegroundColor Cyan
$cmd2 = $conn.CreateCommand()
$cmd2.CommandText = @"
SELECT  id_usuario,
        email,
        activo,
        CASE WHEN fcm_token IS NULL THEN 'NULL'
             ELSE LEFT(fcm_token, 20) + '...'
        END AS token_prefix,
        LEN(fcm_token) AS token_len
FROM    usuario
WHERE   rol = 'ADMIN'
ORDER BY id_usuario
"@
$rdr2 = $cmd2.ExecuteReader()
"{0,5} {1,-30} {2,8} {3,25} {4,10}" -f "id","email","activo","token_prefix","token_len"
"{0,5} {1,-30} {2,8} {3,25} {4,10}" -f ("-" * 5),("-" * 30),("-" * 8),("-" * 25),("-" * 10)
while ($rdr2.Read()) {
    "{0,5} {1,-30} {2,8} {3,25} {4,10}" -f $rdr2["id_usuario"], $rdr2["email"], $rdr2["activo"], $rdr2["token_prefix"], $rdr2["token_len"]
}
$rdr2.Close()

Write-Host ""
Write-Host "=== Detalle todos los usuarios con token (top 10) ===" -ForegroundColor Cyan
$cmd3 = $conn.CreateCommand()
$cmd3.CommandText = @"
SELECT  TOP 10
        id_usuario,
        email,
        rol,
        LEN(fcm_token)  AS token_len
FROM    usuario
WHERE   activo = 1
  AND   fcm_token IS NOT NULL
ORDER BY id_usuario
"@
$rdr3 = $cmd3.ExecuteReader()
"{0,5} {1,-30} {2,-18} {3,10}" -f "id","email","rol","token_len"
"{0,5} {1,-30} {2,-18} {3,10}" -f ("-" * 5),("-" * 30),("-" * 18),("-" * 10)
while ($rdr3.Read()) {
    "{0,5} {1,-30} {2,-18} {3,10}" -f $rdr3["id_usuario"], $rdr3["email"], $rdr3["rol"], $rdr3["token_len"]
}
$rdr3.Close()

$conn.Close()
Write-Host ""
Write-Host "Listo." -ForegroundColor Green
