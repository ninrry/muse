# Find any CJK-corruption pattern: a `?` in a string literal that should be a Chinese char.
# Patterns:
#   1. line has one `"` and ends with `?[,)]}` (or any `?` followed by CJK/ASCII structural char)
#   2. line has one `"` and contains a CJK char followed by `?` (and the next char is non-ASCII or end)
#   3. `?` immediately preceded by a CJK char in a string literal (CJK + ? + structural)
#
# Output: lines that need manual inspection.

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
        # match: content contains `?` near end of line followed by structural char
        if ($line -match '\?[\s\)\,\}\;\.\]]*\s*$') {
            $bad.Add([pscustomobject]@{ File = $file.FullName; Line = $i + 1; Text = $line.Trim() })
        }
    }
}
$bad | Format-Table -AutoSize -Wrap
