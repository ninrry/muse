$ErrorActionPreference = "Stop"
$root = "app\src\test"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt"
foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    $has = $false
    for ($i = 0; $i -lt $bytes.Length - 2; $i++) {
        if ($bytes[$i] -eq 0xE2 -and $bytes[$i + 1] -eq 0x94 -and $bytes[$i + 2] -eq 0x80) {
            $has = $true
            break
        }
    }
    if ($has) { Write-Host $file.FullName }
}
