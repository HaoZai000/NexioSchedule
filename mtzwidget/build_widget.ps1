$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$scriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$innerDir   = Join-Path $scriptDir 'todaycourse\inner'
$widgetZip  = Join-Path $scriptDir 'todaycourse\widget_4x2'
$outerMtz   = Join-Path $scriptDir 'todaycourse\today_course.mtz'
$descXml    = Join-Path $scriptDir 'todaycourse\description.xml'
$previewDir = Join-Path $scriptDir 'todaycourse\preview'

# ---- Step 1: build inner widget zip from the inner folder ----
if (Test-Path -LiteralPath $widgetZip) { Remove-Item -LiteralPath $widgetZip -Force }
$fs = [System.IO.File]::Open($widgetZip, [System.IO.FileMode]::CreateNew)
$zip = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create)
Get-ChildItem -LiteralPath $innerDir -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($innerDir.Length).TrimStart('\', '/').Replace('\', '/')
    $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
    $es = $entry.Open()
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    $es.Write($bytes, 0, $bytes.Length)
    $es.Close()
}
$zip.Dispose(); $fs.Close()
Write-Output "inner zip built: $widgetZip"

# ---- Step 2: build outer .mtz wrapping preview/description/widget ----
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

# Add widget zip
$eWgt = $zip2.CreateEntry('widget_4x2', [System.IO.Compression.CompressionLevel]::Optimal)
$esWgt = $eWgt.Open()
$bWgt = [System.IO.File]::ReadAllBytes($widgetZip)
$esWgt.Write($bWgt, 0, $bWgt.Length); $esWgt.Close()

$zip2.Dispose(); $fs2.Close()
Write-Output "outer mtz built: $outerMtz"