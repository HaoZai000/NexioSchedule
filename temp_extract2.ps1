Add-Type -AssemblyName System.IO.Compression.FileSystem
$src = 'C:\Users\43908\.gradle\caches\9.5.1\transforms\942176d5cf977e560a63e5aab375df8a\transformed\miuix-blur-api.jar'
$dst = 'C:\Users\43908\Desktop\KeCB\temp_miuix_blur_api'
if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
[System.IO.Compression.ZipFile]::ExtractToDirectory($src, $dst)
Get-ChildItem $dst -Recurse -Filter '*.class' | ForEach-Object { $_.FullName }
