Add-Type -AssemblyName System.Drawing

$root = "D:\HDD\MineCraft\plugins\plugins\AurumAuction"
$assets = Join-Path $root "assets"
New-Item -ItemType Directory -Force $assets | Out-Null

function New-RoundedPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-Icon([int]$size, [string]$path) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $rect = New-Object System.Drawing.Rectangle 0, 0, $size, $size
    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect,
        ([System.Drawing.Color]::FromArgb(255, 27, 36, 51)),
        ([System.Drawing.Color]::FromArgb(255, 10, 15, 23)), 45.0
    $g.FillRectangle($bg, $rect)

    $radius = [float]($size * 0.17)
    $inset = [float]($size * 0.035)
    $frame = New-RoundedPath $inset $inset ($size - 2 * $inset) ($size - 2 * $inset) $radius
    $gold = [System.Drawing.Color]::FromArgb(255, 242, 182, 50)
    $goldPen = New-Object System.Drawing.Pen $gold, ([float]($size * 0.018))
    $g.DrawPath($goldPen, $frame)

    # soft glow
    $glowPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $glowPath.AddEllipse([float]($size * 0.18), [float]($size * 0.22), [float]($size * 0.64), [float]($size * 0.64))
    $glow = New-Object System.Drawing.Drawing2D.PathGradientBrush $glowPath
    $glow.CenterColor = [System.Drawing.Color]::FromArgb(60, 46, 204, 113)
    $glow.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 46, 204, 113))
    $g.FillPath($glow, $glowPath)

    # emerald cube
    $cx = [float]($size * 0.30)
    $cy = [float]($size * 0.58)
    $s = [float]($size * 0.19)
    $top = New-Object System.Drawing.Drawing2D.GraphicsPath
    $top.AddPolygon(@(
        (New-Object System.Drawing.PointF $cx, $cy),
        (New-Object System.Drawing.PointF ($cx + $s), ($cy - $s * 0.5)),
        (New-Object System.Drawing.PointF ($cx + 2 * $s), $cy),
        (New-Object System.Drawing.PointF ($cx + $s), ($cy + $s * 0.5))
    ))
    $left = New-Object System.Drawing.Drawing2D.GraphicsPath
    $left.AddPolygon(@(
        (New-Object System.Drawing.PointF $cx, $cy),
        (New-Object System.Drawing.PointF ($cx + $s), ($cy + $s * 0.5)),
        (New-Object System.Drawing.PointF ($cx + $s), ($cy + $s * 1.9)),
        (New-Object System.Drawing.PointF $cx, ($cy + $s * 1.4))
    ))
    $right = New-Object System.Drawing.Drawing2D.GraphicsPath
    $right.AddPolygon(@(
        (New-Object System.Drawing.PointF ($cx + 2 * $s), $cy),
        (New-Object System.Drawing.PointF ($cx + $s), ($cy + $s * 0.5)),
        (New-Object System.Drawing.PointF ($cx + $s), ($cy + $s * 1.9)),
        (New-Object System.Drawing.PointF ($cx + 2 * $s), ($cy + $s * 1.4))
    ))
    $greenTop = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 88, 226, 150))
    $greenLeft = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 30, 160, 94))
    $greenRight = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 20, 118, 72))
    $g.FillPath($greenTop, $top)
    $g.FillPath($greenLeft, $left)
    $g.FillPath($greenRight, $right)
    $g.DrawPath($goldPen, $top)
    $g.DrawPath($goldPen, $left)
    $g.DrawPath($goldPen, $right)

    # gavel
    $state = $g.Save()
    $g.TranslateTransform([float]($size * 0.62), [float]($size * 0.40))
    $g.RotateTransform(-38)
    $handle = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 214, 150, 36))
    $head = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 242, 182, 50))
    $g.FillRectangle($handle, [float](-$size * 0.022), [float](-$size * 0.02), [float]($size * 0.044), [float]($size * 0.30))
    $g.FillRectangle($head, [float](-$size * 0.11), [float](-$size * 0.10), [float]($size * 0.22), [float]($size * 0.075))
    $highlight = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(120, 255, 245, 210))
    $g.FillRectangle($highlight, [float](-$size * 0.10), [float](-$size * 0.095), [float]($size * 0.20), [float]($size * 0.018))
    $g.Restore($state)

    # sparkles
    $spark = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(200, 255, 240, 200))
    foreach ($p in @(@(0.24, 0.28, 0.018), @(0.78, 0.66, 0.012), @(0.70, 0.22, 0.009))) {
        $r = [float]($size * $p[2])
        $g.FillEllipse($spark, [float]($size * $p[0] - $r), [float]($size * $p[1] - $r), 2 * $r, 2 * $r)
    }

    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
}

function New-Banner([string]$iconPath, [string]$path) {
    $w = 1200
    $h = 300
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect,
        ([System.Drawing.Color]::FromArgb(255, 20, 28, 40)),
        ([System.Drawing.Color]::FromArgb(255, 8, 12, 19)), 20.0
    $g.FillRectangle($bg, $rect)

    $icon = [System.Drawing.Image]::FromFile($iconPath)
    $g.DrawImage($icon, 45, 50, 200, 200)

    $gold = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 242, 182, 50))
    $white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 240, 244, 250))
    $gray = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 160, 172, 188))
    $green = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 88, 226, 150))

    $titleFont = New-Object System.Drawing.Font "Segoe UI", 54, ([System.Drawing.FontStyle]::Bold)
    $subFont = New-Object System.Drawing.Font "Segoe UI", 22, ([System.Drawing.FontStyle]::Regular)
    $tagFont = New-Object System.Drawing.Font "Segoe UI", 17, ([System.Drawing.FontStyle]::Regular)

    $g.DrawString("AurumAuction", $titleFont, $gold, 275, 45)
    $g.DrawString("Auction house for Paper / Folia", $subFont, $white, 280, 140)
    $g.DrawString("Bids  -  SQL  -  Multi-server  -  Bedrock", $tagFont, $gray, 282, 185)
    $g.FillRectangle($green, 282, 225, 120, 4)
    $g.DrawString("1.16.5 - 26.x", $tagFont, $green, 418, 215)

    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $icon.Dispose()
    $g.Dispose()
    $bmp.Dispose()
}

$icon512 = Join-Path $assets "icon-512.png"
New-Icon 512 $icon512
New-Icon 128 (Join-Path $assets "icon.png")
New-Banner $icon512 (Join-Path $assets "banner.png")

Get-ChildItem $assets | Select-Object Name, Length
