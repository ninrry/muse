$ErrorActionPreference = "Stop"
$root = "app"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt"
foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    # Strip UTF-8 BOM if present (0xEF 0xBB 0xBF)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $new = $bytes[3..($bytes.Length - 1)]
        [System.IO.File]::WriteAllBytes($file.FullName, $new)
        Write-Host "Stripped BOM from $($file.Name)"
    }
}
