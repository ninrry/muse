# Muse Bug 修复计划书 — Phase 3.1

## 总览

| 编号 | Bug | 严重度 | 涉及文件 | 预计改动 |
|:----|:----|:------|:---------|:--------|
| B1 | 杀后台后播放无响应 | P0 | PlayerState.kt, MuseApp.kt | +20行 |
| B2 | 元数据搜索只显示1个结果 | P1 | MetadataFetcher.kt | +15行 |
| B3 | 专辑/艺术家直接播放而不是显示列表 | P1 | LibraryScreen.kt, MuseNavGraph.kt | +80行 |
| B4 | 生成默认封面预览不更新 | P2 | LibraryScreen.kt | +5行 |
| B5 | 文件夹扫描要手动输入路径 | P2 | SettingsScreen.kt, SettingsViewModel.kt | +20行 |

---

## B1：杀后台后播放无响应

### 根因
`PlayerState.playSongs()` 开头有 `val p = player ?: return` — 如果 MusicService 还没启动、`player` 还是 null，就静默返回，什么都不做。

两种触发场景：
1. **MiniPlayer 点击播放** → 直接调用 `playerState.togglePlayPause()` → `player?.play()` — player 为 null 时无声失败
2. **点击歌曲列表播放** → `startServiceAndPlay()` 先 `startForegroundService()`（异步），立刻 `playerState.playSongs()` — 还没等到 Service onCreate() 执行完

### 修复方案

**修改 `PlayerState.togglePlayPause()`** — 当 player 为 null 时不操作，而是通知调用方需要启动服务（或由调用方保证服务已启动）

**修改 `HomeViewModel.startServiceAndPlay()` / `LibraryViewModel.playSongs()`** — 使用 `bindService` 模式等待服务就绪，或添加重试机制

### 具体改动

#### PlayerState.kt

```kotlin
// Before
fun togglePlayPause() {
    if (_isPlaying.value) player?.pause() else player?.play()
}

// After
fun togglePlayPause() {
    val p = player ?: return
    if (_isPlaying.value) p.pause() else p.play()
}

// Add helper to check if player is ready
fun isPlayerReady(): Boolean = player != null
```

#### MuseApp.kt

确保 MusicService 在应用启动时就初始化：

```kotlin
// Before
override fun onCreate() {
    super.onCreate()
    database
    repository
    // ... auto-scan logic
}

// After  
override fun onCreate() {
    super.onCreate()
    database
    repository
    // Ensure MusicService starts early
    val intent = Intent(this, MusicService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
    // ... auto-scan logic
}
```

#### MiniPlayer (MainActivity.kt / MiniPlayer.kt)

MiniPlayer 的 `onTogglePlayPause` 回调现在需要：先确保 MusicService 启动，再 toggle。

---

## B2：元数据搜索只显示1个结果

### 根因
`searchMusicBrainz()` 的评分排序后，Deezer 补充搜索可能因为 `searchDeezer()` 的 `limit` 参数传递不当而未返回足够结果。另外 `distinctBy { it.title to it.artist }` 会将同名同曲目的不同版本合并为1条。

但真正的问题可能出在：**`MetadataResultSheet` 中 `LazyColumn` 使用了 `key = { i, _ -> i }`**，当结果列表更新时索引重组导致 Compose 无法正确显示新列表。

### 修复方案
在 `MetadataFetcher.searchExact()` 中增加日志输出，并强制 Deezer 搜索即使 MusicBrainz 已有结果也要作为一个补充源。同时优化搜索结果多样性：区分不同专辑/不同来源的结果。

### 具体改动

#### MetadataFetcher.kt

修改 `searchExact()`，确保 Deezer 始终作为补充源：

```kotlin
// Before
if (results.size < maxResults) {
    try {
        val dzResults = searchDeezer(title, artist, maxResults - results.size)
        results.addAll(dzResults)
    } catch (_: Exception) { }
}

// After — always try Deezer too, merge all results
try {
    val dzResults = searchDeezer(title, artist, maxResults)
    results.addAll(dzResults)
} catch (_: Exception) { }
```

同时放宽 `searchDeezer()` 的 `limit` 参数确保返回足够结果。

---

## B3：专辑/艺术家点击不显示列表

### 根因
`AlbumGridView` 和 `ArtistListView` 中，点击卡片直接调用 `playSongs()` 而不是导航到详情页。用户需要的是**先显示该专辑/该艺术家的歌曲列表**，让用户选择哪首歌。

### 修复方案
1. 新增 `AlbumDetailScreen` 和 `ArtistDetailScreen`（或统一用一个 `SongListScreen` 接收参数）
2. 在 `MuseNavGraph.kt` 中添加对应路由
3. 点击专辑/艺术家时导航到详情页，而不是直接播放

### 具体改动

#### 新增文件（或复用现有 SongListView）

在 `MuseNavGraph.kt` 中添加路由：
```kotlin
composable(
    route = "album_detail/{albumTitle}",
    arguments = listOf(navArgument("albumTitle") { type = NavType.StringType })
) { backStackEntry ->
    val albumTitle = backStackEntry.arguments?.getString("albumTitle") ?: ""
    SongListScreen(
        title = "专辑: $albumTitle",
        songs = ... // 从 ViewModel 获取
    )
}
```

或者更简单的方案：**使用 ModalBottomSheet 在当前页展示歌曲列表**，而不是导航到新页面。这样改动更小，且符合 MD3 设计规范。

#### AlbumGridView 改动

```kotlin
// Before
ElevatedCard(
    onClick = {
        viewModel.getSongsByAlbum(album.title) { songs ->
            viewModel.playSongs(songs)
        }
    },
    ...
)

// After — 弹出 BottomSheet 展示歌曲列表
ElevatedCard(
    onClick = {
        viewModel.getSongsByAlbum(album.title) { songs ->
            // 设置到 ViewModel 的状态中，触发 AlbumSongsSheet 显示
            viewModel.showAlbumSongs(album, songs)
        }
    },
    ...
)
```

新增 `AlbumSongsSheet` composable — 一个 ModalBottomSheet 显示歌曲列表，点击歌曲播放。

---

## B4：生成默认封面预览不更新

### 根因
`cover_preview_${song.id}.png` 文件如果之前已经生成过，Coil 的 `AsyncImage` 会把旧的 URI 缓存当作最新内容。

### 修复方案
在写入临时文件前先删除已有文件，或使用时间戳确保 URI 唯一性。

### 具体改动

#### LibraryScreen.kt

```kotlin
// Before
val cacheDir = context.cacheDir
val tempFile = java.io.File(cacheDir, "cover_preview_${song.id}.png")
tempFile.outputStream().use { it.write(defaultBytes) }
selectedArtworkUri = android.net.Uri.fromFile(tempFile)

// After
val cacheDir = context.cacheDir
val tempFile = java.io.File(cacheDir, "cover_preview_${song.id}_${System.nanoTime()}.png")
tempFile.outputStream().use { it.write(defaultBytes) }
selectedArtworkUri = android.net.Uri.fromFile(tempFile)
```

---

## B5：文件夹扫描需要手动输入路径

### 根因
`SettingsScreen.kt` 的 "扫描指定文件夹" 对话框使用 `OutlinedTextField` 让用户手动输入路径，没有调用系统的文件夹选择器。

### 修复方案
使用 Android 的 `ActivityResultContracts.OpenDocumentTree()` (SAF — Storage Access Framework) 让用户通过系统 UI 选择文件夹。

### 具体改动

#### SettingsScreen.kt

新增文件夹选择器 launcher：
```kotlin
val folderPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
) { uri: Uri? ->
    if (uri != null) {
        // 将 content:// URI 转换为文件路径
        val path = getPathFromUri(context, uri)
        viewModel.scanFolder(path)
    }
}
```

同时删除 `showFolderDialog` 和相关 UI，直接用文件夹选择器替代。

如果转换 content:// URI 到文件路径有困难（Android 10+ 沙箱），可以使用 ContentResolver 读取目录下的文件列表。

---

## 风险与注意事项

| 风险 | 影响 | 缓解措施 |
|:----|:-----|:---------|
| B1 中启动 MusicService 时机过早可能增加内存占用 | 轻微 | 只在有歌曲数据时启动 |
| B3 新增 BottomSheet 布局与现有 MiniPlayer 冲突 | 中等 | AlbumSongsSheet 使用 `skipPartiallyExpanded = true` |
| B5 `OpenDocumentTree` 返回 content:// URI 转文件路径不兼容 | 低 | 使用 ContentResolver + 数据库 URI query |
