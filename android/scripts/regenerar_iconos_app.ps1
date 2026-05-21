# =====================================================================
# regenerar_iconos_app.ps1
#
# Genera los recursos del launcher icon a partir de una imagen fuente.
# Reemplaza el icono por defecto de Android Studio (robot verde) por el
# logo Pacem Deus en todas las densidades, mas el adaptive icon de
# Android 8+.
#
# Prerequisito: copiar la imagen del logo (recomendado 1024x1024 o mas)
# a la raiz del proyecto Android como `logo_source.png`:
#
#     C:\ProyectosAndroidStudio\TB02\PacemDeusBodas-TF\logo_source.png
#
# Despues correr este script desde Powershell:
#
#     Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#     .\scripts\regenerar_iconos_app.ps1
#
# El script:
#   1. Verifica que logo_source.png exista
#   2. Para cada densidad mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}:
#        - Genera ic_launcher.png (cuadrado)
#        - Genera ic_launcher_round.png (cuadrado, Android enmascara redondo)
#        - Genera ic_launcher_foreground.png (mas grande con padding
#          para encajar en la safe zone del adaptive icon)
#   3. Sobrescribe mipmap-anydpi-v26/ic_launcher.xml y ic_launcher_round.xml
#      para usar el nuevo foreground PNG y el color launcher_background
#   4. Elimina los drawables vector obsoletos
#      (ic_launcher_foreground.xml, ic_launcher_background.xml)
# =====================================================================

$ErrorActionPreference = "Stop"

$projectRoot = "C:\ProyectosAndroidStudio\TB02\PacemDeusBodas-TF"
$sourcePath  = Join-Path $projectRoot "logo_source.png"
$resPath     = Join-Path $projectRoot "app\src\main\res"

# Validar input
if (-not (Test-Path $sourcePath)) {
    Write-Host "ERROR: No se encontro $sourcePath" -ForegroundColor Red
    Write-Host "Copia la imagen del logo a esa ruta y vuelve a correr." -ForegroundColor Yellow
    exit 1
}

# Cargar System.Drawing para resize de imagenes
Add-Type -AssemblyName System.Drawing

# Tamanos por densidad (tamano standard del launcher icon: 48dp)
$densities = @{
    "mdpi"    = 48
    "hdpi"    = 72
    "xhdpi"   = 96
    "xxhdpi"  = 144
    "xxxhdpi" = 192
}

# Cargar la imagen fuente una sola vez
$source = [System.Drawing.Image]::FromFile($sourcePath)
Write-Host "Imagen fuente: $($source.Width)x$($source.Height) px" -ForegroundColor Cyan

# Funcion auxiliar: redimensiona la imagen fuente a NxN preservando
# calidad alta. Si addPadding=true, deja la imagen escalada al 60% del
# canvas con fondo transparente alrededor (para encajar en la safe zone
# del adaptive icon: 66dp de 108dp = 61%).
function Save-ResizedPng {
    param(
        [Parameter(Mandatory)][int]$Size,
        [Parameter(Mandatory)][string]$OutputPath,
        [bool]$AddPadding = $false
    )

    $bitmap = New-Object System.Drawing.Bitmap($Size, $Size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

    if ($AddPadding) {
        # Imagen al 60% centrada, resto transparente
        $innerSize = [int]($Size * 0.60)
        $offset = [int](($Size - $innerSize) / 2)
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.DrawImage($source, $offset, $offset, $innerSize, $innerSize)
    } else {
        # Imagen completa rellenando el canvas
        $graphics.DrawImage($source, 0, 0, $Size, $Size)
    }

    $graphics.Dispose()

    $outDir = Split-Path -Parent $OutputPath
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

# Generar PNGs en cada densidad
Write-Host ""
Write-Host "Generando mipmaps..." -ForegroundColor Cyan
foreach ($entry in $densities.GetEnumerator()) {
    $density = $entry.Key
    $size = $entry.Value
    $mipmapDir = Join-Path $resPath "mipmap-$density"

    Save-ResizedPng -Size $size -OutputPath (Join-Path $mipmapDir "ic_launcher.png")
    Save-ResizedPng -Size $size -OutputPath (Join-Path $mipmapDir "ic_launcher_round.png")

    # El foreground del adaptive icon es 108x108dp pero la safe zone
    # central es 66x66dp. Generamos al tamano del launcher x 2.25 para
    # tener resolucion al cuadruple del icono base, y agregamos padding
    # para que el logo quede en la safe zone.
    $foregroundSize = [int]($size * 2.25)
    Save-ResizedPng -Size $foregroundSize -OutputPath (Join-Path $mipmapDir "ic_launcher_foreground.png") -AddPadding $true

    Write-Host "  $density ($size x $size) generado" -ForegroundColor Gray
}

# Reescribir los adaptive icon XMLs para que usen el nuevo foreground
# y el color de fondo dorado.
Write-Host ""
Write-Host "Actualizando adaptive icon XMLs..." -ForegroundColor Cyan

$adaptiveDir = Join-Path $resPath "mipmap-anydpi-v26"
$adaptiveXml = @"
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"@

Set-Content -Path (Join-Path $adaptiveDir "ic_launcher.xml") -Value $adaptiveXml -Encoding UTF8
Set-Content -Path (Join-Path $adaptiveDir "ic_launcher_round.xml") -Value $adaptiveXml -Encoding UTF8
Write-Host "  ic_launcher.xml reescrito" -ForegroundColor Gray
Write-Host "  ic_launcher_round.xml reescrito" -ForegroundColor Gray

# Eliminar los drawables vector obsoletos (robot verde + grid)
Write-Host ""
Write-Host "Eliminando drawables obsoletos..." -ForegroundColor Cyan

$obsoletos = @(
    (Join-Path $resPath "drawable\ic_launcher_foreground.xml"),
    (Join-Path $resPath "drawable\ic_launcher_background.xml")
)
foreach ($f in $obsoletos) {
    if (Test-Path $f) {
        Remove-Item -Path $f -Force
        Write-Host "  Eliminado: $($f -replace [regex]::Escape($projectRoot), '')" -ForegroundColor Gray
    }
}

# Cleanup
$source.Dispose()

Write-Host ""
Write-Host "Listo. Rebuildea la app en Android Studio para ver los iconos nuevos." -ForegroundColor Green
Write-Host "Si el launcher de Android cachea el icono viejo, desinstala y reinstala la app." -ForegroundColor Yellow
