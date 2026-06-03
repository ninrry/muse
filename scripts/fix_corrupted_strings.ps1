# Fix corrupted string literals: a line with exactly one `"` that ends with
# `?,` (or `?, `) has its closing `"` stripped. The original ended with `",`.
#
# Pattern we look for: line contains exactly one `"` and ends with `?,`.

$ErrorActionPreference = "Stop"
$root = "app"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt"
$fixedCount = 0
foreach ($file in $files) {
    $lines = Get-Content -LiteralPath $file.FullName
    $changed = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $quoteCount = ($line.ToCharArray() | Where-Object { $_ -eq '"' }).Count
        if ($quoteCount -ne 1) { continue }
        # Only fix when the line ends with `?,` (no quote between the `?` and end).
        if ($line -match '\?,\s*$') {
            $new = $line -replace '\?,\s*$', '",'
            $lines[$i] = $new
            $changed = $true
            $fixedCount++
            Write-Host "$($file.Name):$($i + 1)  $($line.Trim())  ->  $($new.Trim())"
        }
    }
    if ($changed) {
        Set-Content -LiteralPath $file.FullName -Value ($lines -join "`n") -NoNewline
    }
}
Write-Host "Fixed $fixedCount corrupted string literals"
