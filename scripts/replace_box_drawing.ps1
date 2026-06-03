$ErrorActionPreference = "Stop"
$root = "app"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt"
foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    $content = [System.Text.Encoding]::UTF8.GetString($bytes)
    # Replace box-drawing char ─ (U+2500) with ASCII dashes
    $new = $content -replace ([char]0x2500), '-'
    if ($new -ne $content) {
        Set-Content -LiteralPath $file.FullName -Value $new -NoNewline -Encoding UTF8
        Write-Host "Updated $($file.Name)"
    }
}
