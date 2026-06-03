$ErrorActionPreference = "Stop"
$bytes = [System.IO.File]::ReadAllBytes("app\build.gradle.kts")
# Find any byte that is not in the printable ASCII range
$nonAscii = for ($i = 0; $i -lt $bytes.Length; $i++) {
    $b = $bytes[$i]
    if ($b -lt 32 -or $b -gt 126) {
        # Skip LF, CR, TAB
        if ($b -ne 10 -and $b -ne 13 -and $b -ne 9) {
            [pscustomobject]@{ Offset = $i; Byte = $b; Hex = "0x$("{0:X2}" -f $b)" }
        }
    }
}
$nonAscii | Format-Table -AutoSize
