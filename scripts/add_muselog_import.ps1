# Add luzzr.muse.core.log.MuseLog import to files that reference MuseLog but don't import it.
$ErrorActionPreference = "Stop"
$root = "app\src\main\java\luzzr\muse"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt" | Where-Object { $_.FullName -notmatch "core\\log\\MuseLog" }

foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    if (-not ($content -match "MuseLog\.")) { continue }
    if ($content -match "import luzzr\.muse\.core\.log\.MuseLog") { continue }

    # Find the last `import` line and insert MuseLog after it.
    $lines = $content -split "`n"
    $lastImportIdx = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^import ') { $lastImportIdx = $i }
    }
    if ($lastImportIdx -lt 0) {
        Write-Host "No imports found in $($file.Name), skipping"
        continue
    }
    $insertLine = "import luzzr.muse.core.log.MuseLog"
    $newLines = @()
    $newLines += $lines[0..$lastImportIdx]
    $newLines += $insertLine
    if ($lastImportIdx + 1 -lt $lines.Count) {
        $newLines += $lines[($lastImportIdx + 1)..($lines.Count - 1)]
    }
    Set-Content -LiteralPath $file.FullName -Value ($newLines -join "`n") -NoNewline
    Write-Host "Imported MuseLog in $($file.Name)"
}
