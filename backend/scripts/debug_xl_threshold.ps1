$ErrorActionPreference = "Continue"
$conn = New-Object System.Data.SqlClient.SqlConnection(
    "Server=$($env:PACEM_RDS_HOST),1433;Database=$($env:PACEM_RDS_DB);User Id=$($env:PACEM_RDS_USER);Password=$($env:PACEM_RDS_PWD);Encrypt=True;TrustServerCertificate=True;"
)
$conn.Open()

# Estructura de la tabla
$cmd = $conn.CreateCommand()
$cmd.CommandText = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'configuracion_precios' AND (COLUMN_NAME LIKE '%xl%' OR COLUMN_NAME LIKE '%pasajero%' OR COLUMN_NAME LIKE '%umbral%')"
$rdr = $cmd.ExecuteReader()
Write-Host "=== Columnas XL/pasajeros ===" -ForegroundColor Cyan
while ($rdr.Read()) { "{0,-50} {1,20}" -f $rdr["COLUMN_NAME"], $rdr["DATA_TYPE"] }
$rdr.Close()

# Valores actuales
$cmd2 = $conn.CreateCommand()
$cmd2.CommandText = "SELECT movilidad_xl_umbral_pasajeros, movilidad_xl_factor, mov_distancia_libre_km FROM configuracion_precios"
$rdr2 = $cmd2.ExecuteReader()
Write-Host ""
Write-Host "=== Valores actuales ===" -ForegroundColor Cyan
while ($rdr2.Read()) {
    "xl_umbral_pasajeros: $($rdr2['movilidad_xl_umbral_pasajeros'])"
    "xl_factor:           $($rdr2['movilidad_xl_factor'])"
    "distancia_libre_km:  $($rdr2['mov_distancia_libre_km'])"
}
$rdr2.Close()
$conn.Close()
