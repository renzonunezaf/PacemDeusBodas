# Hook: valida que cada CREATE/ALTER PROC tenga GO antes
param([string[]]$Files)

$errores = @()
foreach ($file in $Files) {
    if (-not $file.EndsWith('.sql')) { continue }
    if (-not (Test-Path $file)) { continue }

    $lines = Get-Content $file
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i].Trim()
        if ($line -match '^(CREATE|ALTER)\s+(PROC|PROCEDURE)\s') {
            # Buscar GO en lineas previas no vacias
            $prevIdx = $i - 1
            while ($prevIdx -ge 0 -and $lines[$prevIdx].Trim() -eq '') { $prevIdx-- }
            if ($i -gt 0 -and $prevIdx -ge 0 -and $lines[$prevIdx].Trim() -notmatch '^GO\s*$') {
                $errores += "${file}:$($i+1) - CREATE/ALTER PROC sin GO previo"
            }
        }
    }
}

if ($errores.Count -gt 0) {
    Write-Host "Errores SQL GO:" -ForegroundColor Red
    $errores | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
exit 0
