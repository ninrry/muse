# Final scan: find any string that contains a `?` immediately followed by a
# non-CJK context — sign of CJK corruption (original chars replaced by `?`).

$ErrorActionPreference = "Stop"
$root = "app"
$files = Get-ChildItem -Path $root -Recurse -Filter "*.kt"
$bad = [System.Collections.Generic.List[object]]::new()
foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $lines = $content -split "`n"
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        # In a string literal: text contains a `?` followed by a closing quote.
        # Heuristic: line has exactly one `"` and a `?` is followed by some other ASCII char.
        $quoteCount = ($line.ToCharArray() | Where-Object { $_ -eq '"' }).Count
        if ($quoteCount -ne 1) { continue }
        # Find `?` followed by anything other than another `?` or non-ASCII
        if ($line -match '"[^"]*\?[A-Za-z0-9_ ]') {
            $bad.Add([pscustomobject]@{ File = $file.FullName; Line = $i + 1; Text = $line.Trim() })
        }
    }
}
$bad | Format-Table -AutoSize -Wrap
