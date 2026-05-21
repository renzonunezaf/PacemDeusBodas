# Hook: valida que archivos en lambdas/ sigan convencion verbo_entidad_accion.py
param([string[]]$Files)

$validVerbs = @('get', 'post', 'put', 'delete', 'patch')
$errores = @()

foreach ($file in $Files) {
    if (-not $file.EndsWith('.py')) { continue }
    if (-not ($file -match 'lambdas[/\\][^/\\]+[/\\]')) { continue }
    if ($file -match 'shared[/\\]') { continue }
    if ($file -match '__init__\.py$') { continue }
    if ($file -match 'lambda_function\.py$') { continue }
    if ($file -match 'pdf_builder\.py$') { continue }

    $basename = Split-Path $file -Leaf
    $name = [System.IO.Path]::GetFileNameWithoutExtension($basename)
    $parts = $name -split '_'

    if ($parts.Count -lt 2) {
        $errores += "${file}: nombre no sigue convencion verbo_entidad_accion"
        continue
    }

    $verb = $parts[0].ToLower()
    if ($verb -notin $validVerbs) {
        $errores += "${file}: verbo '$verb' no valido (esperado: $($validVerbs -join ', '))"
    }
}

if ($errores.Count -gt 0) {
    Write-Host "Errores Lambda naming:" -ForegroundColor Red
    $errores | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
exit 0
