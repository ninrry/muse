$ErrorActionPreference = "Stop"
$root = "app"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt"
$bad = [System.Collections.Generic.List[object]]::new()
foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $lines = $content -split "`n"
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        # Find `?` followed by `)` at end of a string literal — CJK corruption marker
        if ($line -match '"[^"]*\?\s*\)') {
            $bad.Add([pscustomobject]@{ File = $file.FullName; Line = $i + 1; Text = $line.Trim() })
        }
    }
}
$bad | Format-Table -AutoSize -Wrap
