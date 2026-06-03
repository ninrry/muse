# More reliable detector that reads files explicitly as UTF-8.
$ErrorActionPreference = "Stop"
$root = "app"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt"
$bad = [System.Collections.Generic.List[object]]::new()
foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $lines = $content -split "`n"
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $quoteCount = ($line.ToCharArray() | Where-Object { $_ -eq '"' }).Count
        if ($quoteCount -ne 1) { continue }
        if ($line -match '\?,\s*$') {
            $bad.Add([pscustomobject]@{ File = $file.FullName; Line = $i + 1; Text = $line.Trim() })
        }
    }
}
$bad | Format-Table -AutoSize -Wrap
