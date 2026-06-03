# Sorts the `import` statements in each Kotlin file using the standard
# ktlint ordering rules:
#   1. blank line after the package
#   2. all `import` lines are grouped and sorted alphabetically
#
# Leaves comments and code untouched.

$ErrorActionPreference = "Stop"
$root = "app\src\main\java\luzzr\muse"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt"

foreach ($file in $files) {
    $lines = Get-Content -LiteralPath $file.FullName

    # find package line
    $pkgIdx = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*package\s+') { $pkgIdx = $i; break }
    }
    if ($pkgIdx -lt 0) { continue }

    # find the first non-import, non-blank line after package
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

    # Collect imports
    $imports = @()
    for ($i = $importStart; $i -le $importEnd; $i++) {
        if ($lines[$i] -match '^\s*import\s+') { $imports += $lines[$i] }
    }
    if ($imports.Count -le 1) { continue }

    # Group: anything with `luzzr.muse` first, then everything else, sorted.
    $local = $imports | Where-Object { $_ -match 'import luzzr\.muse' } | Sort-Object
    $third = $imports | Where-Object { $_ -notmatch 'import luzzr\.muse' -and $_ -notmatch 'import (java|javax|kotlin|kotlinx|android|androidx|com|org|net|io)' } | Sort-Object
    $stdlib = $imports | Where-Object { $_ -match 'import (java|javax|kotlin|kotlinx|android|androidx|com|org|net|io)' } | Sort-Object

    $newImports = @()
    if ($stdlib.Count -gt 0) { $newImports += $stdlib; $newImports += "" }
    if ($local.Count -gt 0) { $newImports += $local }

    $before = $lines[0..($importStart - 1)]
    $after = $lines[($importEnd + 1)..($lines.Count - 1)]

    # Strip leading blank lines from `after` to avoid double blank.
    while ($after.Count -gt 0 -and $after[0] -match '^\s*$') { $after = $after[1..($after.Count - 1)] }

    $merged = @()
    $merged += $before
    $merged += $newImports
    $merged += ""
    $merged += $after

    Set-Content -LiteralPath $file.FullName -Value ($merged -join "`n") -NoNewline
}
