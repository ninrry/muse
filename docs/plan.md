# Muse Phase 3 — Bug修复 + 封面编辑 + 元数据增强

## 任务拆解

### T1: 修复自动播放 Bug

**根因：** `MuseApp.kt:47-62` 在 `onCreate()` 中检测到数据库有歌曲就自动播放第一首。

**修复：** 删除自动播放代码块。App 启动后不自动播放音乐。

| 文件 | 变更 | Before | After |
|:-----|:-----|:-------|:------|
| `MuseApp.kt` | 删除 auto-play 块 | 47-62 行自动扫描+播放 | 只保留数据库检查，去掉播放逻辑 |

---

### T2: 封面元数据编辑 + 默认封面生成

**子任务：**

#### T2a: 编辑对话框增加封面选择
- 编辑元数据对话框中增加"选择封面"按钮
- 使用 Android PhotoPicker (`PickVisualMedia`) 选择图片
- 选定后预览封面缩略图
- 保存时将封面图片写入音频文件 (jaudiotagger `Artwork`)

#### T2b: 默认封面生成 (纯色背景 + 歌曲名称)
- 没有封面时自动生成一个 Bitmap
- 纯色背景 (Material You 颜色或随机柔色)
- 歌名最多显示 8 个字符，超出截断+省略号
- 字数自适应字体大小 (2字=超大, 4字=大, 6字=中, 8字=小)
- 保存到文件 + 写入 jaudiotagger Artwork

#### T2c: 更新封面显示
- 所有封面展示位置 (MiniPlayer、全屏播放器、曲库列表、专辑页) 读取生成的默认封面
- 原则：优先显示文件内嵌封面 (jaudiotagger 读取)，没有则显示默认封面

**涉及文件：**
| 文件 | 变更 |
|:-----|:-----|
| `TagEditor.kt` | 新增 `readArtwork()` / `writeArtwork()` / `writeArtworkBitmap()` 方法 |
| `MusicRepository.kt` | `updateSongTags()` 中传递 artwork |
| `LibraryViewModel.kt` | `saveEditedMetadata()` 增加封面参数 |
| `LibraryScreen.kt` | 编辑对话框增加封面选择UI |
| 新建 `DefaultCoverGenerator.kt` | 纯色背景+文字 Bitmap 生成 |

---

### T3: 元数据抓取增强

**当前问题：** `MetadataFetcher.search()` 直接使用当前歌曲的 title/artist 并经过 `sanitizeQuery()` 处理，用户无法干预搜索结果。

**改进方案：** 抓取前展示一个**搜索参数编辑对话框**，默认使用当前歌曲的元数据，用户可以修改后再搜索。搜索时不经过 `sanitizeQuery()`。

| 文件 | 变更 | Before | After |
|:-----|:-----|:-------|:------|
| `MetadataFetcher.kt` | 新增 `searchExact()` 方法 | `search()` 自动 sanitize | `searchExact()` 用用户输入的精确值搜索 |
| `LibraryViewModel.kt` | 新增搜索前编辑流程 | `searchMetadata()` 直接搜 | 先弹编辑对话框→用户确认→再搜 |
| `LibraryScreen.kt` | 新增搜索参数编辑对话框 | 无 | 标题+艺术家输入框，预填当前值 |

---

## 文件变更汇总

| 文件 | 新增/修改 | 预估行数 |
|:-----|:---------|:--------:|
| `MuseApp.kt` | 修改 ✅ 删除 ~15行 | -15 |
| `TagEditor.kt` | 修改 ✅ 新增 artwork 读写 | +60 |
| `MusicRepository.kt` | 修改 ✅ 传递 artwork | +10 |
| `LibraryViewModel.kt` | 修改 ✅ 封面选择+搜索前编辑 | +50 |
| `LibraryScreen.kt` | 修改 ✅ UI 改动 | +100 |
| **新** `DefaultCoverGenerator.kt` | 新增 | +80 |
| `MetadataFetcher.kt` | 修改 ✅ 新增 searchExact | +20 |
| **总计** | **6改1新** | **~305行** |
