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
        if ($quoteCount -ne 0) { continue }
        # Look for `?` followed by `,` at end of line — sign of corrupted CJK + missing closing quote.
        if ($line -match '\?,\s*$') {
            # Skip if line is part of a string template (`${...}`)
            if ($line -match '\$\{') { continue }
            $bad.Add([pscustomobject]@{ File = $file.FullName; Line = $i + 1; Text = $line.Trim() })
        }
    }
}
$bad | Format-Table -AutoSize -Wrap
