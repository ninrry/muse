# For each corrupted line, dump the raw UTF-8 bytes so we can see what the
# original CJK character sequence was before display corruption.

$lines_to_check = @(
    @{ file = "app\src\androidTest\java\luzzr\muse\ui\components\LyricsViewTest.kt"; line = 105 },
    @{ file = "app\src\androidTest\java\luzzr\muse\ui\components\MiniPlayerTest.kt"; line = 28 },
    @{ file = "app\src\main\java\luzzr\muse\data\network\SearchMatch.kt"; line = 31 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\home\HomeScreen.kt"; line = 101 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\library\LibraryScreen.kt"; line = 307 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\library\LibraryScreen.kt"; line = 318 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\library\LibraryScreen.kt"; line = 460 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\library\LibraryScreen.kt"; line = 513 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\library\MetadataResultSheet.kt"; line = 66 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\player\LyricsPanel.kt"; line = 203 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\player\LyricsPanel.kt"; line = 210 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\player\PlayerControls.kt"; line = 219 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\player\PlayerControls.kt"; line = 257 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\player\SleepTimerDialog.kt"; line = 84 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\settings\SettingsScreen.kt"; line = 167 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\settings\SettingsScreen.kt"; line = 174 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\settings\SettingsScreen.kt"; line = 175 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\settings\SettingsScreen.kt"; line = 213 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\settings\SettingsScreen.kt"; line = 241 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\settings\SettingsScreen.kt"; line = 268 },
    @{ file = "app\src\main\java\luzzr\muse\ui\screens\settings\SettingsScreen.kt"; line = 285 }
)

foreach ($entry in $lines_to_check) {
    Write-Host "=== $($entry.file):$($entry.line) ==="
    $lines = Get-Content -LiteralPath $entry.file
    $line = $lines[$entry.line - 1]
    Write-Host "TEXT: $line"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($line)
    Write-Host ("BYTES: " + ($bytes -join " "))
    Write-Host ""
}
