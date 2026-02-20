Add-Type -AssemblyName System.Drawing

$srcPath = "d:\Documents\android\Embedded_Systems_Career_Guide\pictures\icon\icon.png"
$resBase = "d:\Documents\android\Embedded_Systems_Career_Guide\app\src\main\res"

$src = [System.Drawing.Image]::FromFile($srcPath)
Write-Host "Source size: $($src.Width)x$($src.Height)"

$sizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($entry in $sizes.GetEnumerator()) {
    $dir = Join-Path $resBase $entry.Key
    $size = $entry.Value

    # Create resized bitmap with high quality
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.DrawImage($src, 0, 0, $size, $size)
    $g.Dispose()

    # Remove old webp files
    $webpPath = Join-Path $dir "ic_launcher.webp"
    $webpRoundPath = Join-Path $dir "ic_launcher_round.webp"
    if (Test-Path $webpPath) { Remove-Item $webpPath }
    if (Test-Path $webpRoundPath) { Remove-Item $webpRoundPath }

    # Save as PNG (Android supports PNG in mipmap folders)
    $pngPath = Join-Path $dir "ic_launcher.png"
    $pngRoundPath = Join-Path $dir "ic_launcher_round.png"
    $bmp.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save($pngRoundPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    Write-Host "Created ${size}x${size} icons in $($entry.Key)"
}

$src.Dispose()
Write-Host "Done! All icons generated."
