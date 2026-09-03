$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$scriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$descXml    = Join-Path $scriptDir 'todaycourse\description.xml'
$previewDir = Join-Path $scriptDir 'todaycourse\preview'

# ---- Steps 1: build inner widget zips from the inner folders ----
function Build-InnerZip {
    param(
        [string]$InnerDir,
        [string]$OutZip
    )
    if (Test-Path -LiteralPath $OutZip) { Remove-Item -LiteralPath $OutZip -Force }
    $fs = [System.IO.File]::Open($OutZip, [System.IO.FileMode]::CreateNew)
    $zip = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create)
    Get-ChildItem -LiteralPath $InnerDir -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($InnerDir.Length).TrimStart('\', '/').Replace('\', '/')
        $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
        $es = $entry.Open()
        $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
        $es.Write($bytes, 0, $bytes.Length)
        $es.Close()
    }
    $zip.Dispose(); $fs.Close()
    Write-Output "inner zip built: $OutZip"
}

$todayRoot = Join-Path $scriptDir 'todaycourse'

# 4x2 组件
$w4Inner  = Join-Path $todayRoot 'inner'
$w4Zip    = Join-Path $todayRoot 'widget_4x2'
Build-InnerZip -InnerDir $w4Inner -OutZip $w4Zip

# 2x2 组件
$w2Inner  = Join-Path $todayRoot 'inner2x2'
$w2Zip    = Join-Path $todayRoot 'widget_2x2'
Build-InnerZip -InnerDir $w2Inner -OutZip $w2Zip

# ---- Step 2: build outer .mtz wrapping preview/description/widgets ----
$outerMtz = Join-Path $todayRoot 'today_course.mtz'
if (Test-Path -LiteralPath $outerMtz) { Remove-Item -LiteralPath $outerMtz -Force }
$fs2 = [System.IO.File]::Open($outerMtz, [System.IO.FileMode]::CreateNew)
$zip2 = New-Object System.IO.Compression.ZipArchive($fs2, [System.IO.Compression.ZipArchiveMode]::Create)

# Add preview directory and files
[void]$zip2.CreateEntry('preview/', [System.IO.Compression.CompressionLevel]::Optimal)
if (Test-Path -LiteralPath $previewDir) {
    Get-ChildItem -LiteralPath $previewDir -Recurse -File | ForEach-Object {
        $rel = 'preview/' + $_.Name
        $entry = $zip2.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
        $es = $entry.Open()
        $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
        $es.Write($bytes, 0, $bytes.Length)
        $es.Close()
    }
}

# Add description.xml
$eDesc = $zip2.CreateEntry('description.xml', [System.IO.Compression.CompressionLevel]::Optimal)
$esDesc = $eDesc.Open()
$bDesc = [System.IO.File]::ReadAllBytes($descXml)
$esDesc.Write($bDesc, 0, $bDesc.Length); $esDesc.Close()

# Add each widget zip (4x2 + 2x2)
foreach ($pair in @(@{ EntryName = 'widget_4x2'; ZipPath = $w4Zip }, @{ EntryName = 'widget_2x2'; ZipPath = $w2Zip })) {
    $eWgt = $zip2.CreateEntry($pair.EntryName, [System.IO.Compression.CompressionLevel]::Optimal)
    $esWgt = $eWgt.Open()
    $bWgt = [System.IO.File]::ReadAllBytes($pair.ZipPath)
    $esWgt.Write($bWgt, 0, $bWgt.Length); $esWgt.Close()
}

$zip2.Dispose(); $fs2.Close()
Write-Output "outer mtz built: $outerMtz"