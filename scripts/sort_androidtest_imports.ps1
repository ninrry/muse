#!/usr/bin/env pwsh
# Reorder imports in androidTest files to satisfy ktlint:
#   - All third-party (androidx, junit, compose, etc.) imports first, sorted.
#   - Blank line.
#   - Project imports (luzzr.muse.*) after, sorted.

$ErrorActionPreference = "Stop"
$root = "app\src\androidTest"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt"

foreach ($file in $files) {
    $lines = Get-Content -LiteralPath $file.FullName
    $pkgIdx = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*package\s+') { $pkgIdx = $i; break }
    }
    if ($pkgIdx -lt 0) { continue }

    $importStart = -1
    $importEnd = -1
    for ($i = $pkgIdx + 1; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*import\s+') {
            if ($importStart -lt 0) { $importStart = $i }
            $importEnd = $i
        } elseif ($importStart -ge 0 -and $lines[$i] -notmatch '^\s*$') {
            break
        }
    }
    if ($importStart -lt 0) { continue }

    $imports = @()
    for ($i = $importStart; $i -le $importEnd; $i++) {
        if ($lines[$i] -match '^\s*import\s+') { $imports += $lines[$i] }
    }

    $local = $imports | Where-Object { $_ -match 'import luzzr\.muse' } | Sort-Object
    $third = $imports | Where-Object { $_ -notmatch 'import luzzr\.muse' } | Sort-Object

    $newImports = @()
    if ($third.Count -gt 0) { $newImports += $third; $newImports += "" }
    if ($local.Count -gt 0) { $newImports += $local }

    $before = $lines[0..($importStart - 1)]
    $after = $lines[($importEnd + 1)..($lines.Count - 1)]
    while ($after.Count -gt 0 -and $after[0] -match '^\s*$') { $after = $after[1..($after.Count - 1)] }

    $merged = @()
    $merged += $before
    $merged += $newImports
    $merged += ""
    $merged += $after

    Set-Content -LiteralPath $file.FullName -Value ($merged -join "`n") -NoNewline
}
