# Replace android.util.Log calls with MuseLog across the codebase.
# Idempotent: if a file already imports MuseLog or has no Log calls, it is skipped.

$ErrorActionPreference = "Stop"
$root = "app\src\main\java\luzzr\muse"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt" | Where-Object { $_.FullName -notmatch "core\\log\\MuseLog" }

foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    if (-not ($content -match "android\.util\.Log\.[deviw]\(")) { continue }

    # Replace each android.util.Log.{d,e,i,w} with MuseLog.{d,e,i,w}.
    $updated = $content `
        -replace "android\.util\.Log\.d\(",  "MuseLog.d("  `
        -replace "android\.util\.Log\.e\(",  "MuseLog.e("  `
        -replace "android\.util\.Log\.i\(",  "MuseLog.i("  `
        -replace "android\.util\.Log\.w\(",  "MuseLog.w("  `
        -replace "android\.util\.Log\.v\(",  "MuseLog.d("

    if ($updated -ne $content) {
        Set-Content -LiteralPath $file.FullName -Value $updated -NoNewline
        Write-Host "Updated $($file.Name)"
    }
}
