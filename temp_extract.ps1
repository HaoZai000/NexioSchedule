Add-Type -AssemblyName System.IO.Compression.FileSystem
$src = 'C:\Users\43908\.gradle\caches\9.5.1\transforms\f027793ea4f4a4b2b63f3c546b6c4888\transformed\backdrop-api.jar'
$dst = 'C:\Users\43908\Desktop\KeCB\temp_backdrop_api'
if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
[System.IO.Compression.ZipFile]::ExtractToDirectory($src, $dst)
Get-ChildItem $dst -Recurse -Filter '*.class' | ForEach-Object { $_.FullName }
