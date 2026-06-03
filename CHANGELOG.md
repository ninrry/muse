# Changelog

## [Unreleased]

### Added
- Timber 日志系统（MuseLog wrapper）
- AppException 错误层级 + ErrorMapper
- ktlint + detekt 代码质量检查
- GitHub Actions CI（lint / unit-test / android-test / build）
- 9 个 UseCase（ScanAllSongs / ScanFolder / DeleteSong / EditSongMetadata / SearchMetadata / ApplyMetadata / FetchLyrics / RestoreSession / SetSleepTimer）
- MuseDimens 语义化 dimension tokens
- 600/840dp 响应式布局适配
- fontScale 防溢出（0.8~1.3 限制）
- UiState/UiEvent/UiEffect 模式全面化
- WorkManager Workers（PeriodicScan / PeriodicCoverGen / SessionRestore）
- BootReceiver 开机自启
- PermissionManager 权限管理
- 47 个新单元测试（总计 193 个）
- 10 个文档文件（README / ARCHITECTURE / DATABASE / TESTING / PERMISSIONS / CHANGELOG + 4 个 ADR）

### Changed
- MusicRepository → MusicRepositoryFacade（重命名）
- UpdateSongTagsUseCase → EditSongMetadataUseCase（重命名）
- SongRepository 拆分为 MediaStoreScanner + MetadataFileWriter + SongRepository
- LibraryScreen 拆分为 LibraryRoute + SongListTab + AlbumListTab + ArtistListTab + 2 Dialogs
- LibraryViewModel 拆分为 LibrarySearchState + LibraryMetadataState + LibraryEditState + LibraryViewModel
- PlayerViewModel 拆分为 LyricsStateHolder + SessionRestoreManager + PlayerViewModel
- MainActivity 拆分为 MainViewModel + MuseScaffold + MuseNavHost + SystemBarsEffect + MainActivity
- SettingsScreen 拆分为 7 个 component 文件 + SettingsUtils + SettingsScreen
- 所有硬编码 dp 值替换为 MuseDimens tokens
- 所有 `android.util.Log` 替换为 `MuseLog`
- 所有 CJK 字符串 corruption 修复
- 所有 UTF-8 BOM 剥离
- 所有 box-drawing 字符替换为 ASCII

### Fixed
- 21 处 CJK 字符串 corruption
- 11 个 .kt 文件 UTF-8 BOM
- 所有 Hilt/Dagger/Timber/Coil 缺失 import
- detekt 51 issues → 0
- ktlint 全部通过

## [1.1.0] - 2024-01-01

### Added
- 初始版本发布
- 基础音乐播放功能
- 歌词显示
- 元数据编辑
- 封面生成
