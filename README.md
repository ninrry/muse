# Muse 🎵

**安静、克制、温暖**的 Android 本地音乐播放器。

> Muse 不是「能用的播放器」。它是**数字唱片馆**——每个像素、每次交互、每帧动画都在传递「安静、克制、温暖」的情绪。

<p align="center">
  <img src="docs/screenshots/home.png" width="200" alt="首页" />
  <img src="docs/screenshots/player.png" width="200" alt="播放页" />
  <img src="docs/screenshots/lyrics.png" width="200" alt="歌词" />
  <img src="docs/screenshots/library.png" width="200" alt="曲库" />
</p>

---

## ✨ 功能总览

### 🎵 音频引擎

- **Media3 ExoPlayer** 核心播放引擎
- 全格式支持：

| 格式 | 类型 |
|:----|:-----|
| MP3, AAC, OGG (Vorbis) | 有损压缩 |
| FLAC, ALAC, WAV, AIFF | 无损 |
| **Opus**, MP4A (AAC/ALAC) | 现代格式 |
| APE (Monkey's Audio) | 高清无损 |
| AC-3, E-AC-3 (Dolby Digital) | 环绕声 |

- 智能音频焦点处理（接听电话自动暂停）
- 50ms 精度的进度更新（支持精确拖动）

### 🎨 设计系统

完整的 Material Design 3 实现：

- **暖米色系调色板** — 亮色/暗色双主题完美覆盖
- 全 MD3 色彩角色：`primary` / `secondary` / `tertiary` / `surfaceContainer` / `surfaceVariant` / `outlineVariant` 等
- 5 级 surface container 层级（`Lowest` → `Low` → `Default` → `High` → `Highest`）
- 自定义 Shape 系统：卡片 28dp 圆角、封面 24dp、BottomSheet 32dp
- 完整 MD3 Typography Scale（14 级字号）
- **M3 动画规范** — Standard / Emphasized Decelerate / Emphasized Accelerate easing 曲线，5 种 spring 预设

### 📂 扫描系统

两种扫描模式自由选择：

- **全盘自动扫描** — 多 URI 兼容（MediaStore `VOLUME_EXTERNAL`/`external`/`EXTERNAL_CONTENT_URI`），覆盖 HyperOS / MIUI / 原生 Android
- **指定文件夹扫描** — 使用系统 SAF 文件选择器选择任意文件夹
- 扫描统计 — 实时显示歌曲/专辑/艺术家数量及耗时
- 增量扫描 — 支持 `scanFolder()` 不重复已存歌曲

### 🏷️ 元数据管理

- **自动解析**：标题 / 艺术家 / 专辑 / 专辑ID / 时长 / 编码格式 / 码率 / 采样率 / 位深 / 声道 / 文件大小 / 曲目号 / 年代
- **自动编码检测**：通过扩展名 + MIME 双重判断（MP3 / FLAC / AAC / Opus / ALAC / WAV / OGG）
- **手动编辑**：在曲库中长按歌曲 → 「编辑元数据」，可修改标题 / 艺术家 / 专辑 / 年代 / 流派
- **自动搜索元数据**：从 MusicBrainz 自动补全歌曲信息
- **重命名**：长按歌曲 → 「重命名」，快速修改标题
- **删除**：长按歌曲 → 「删除」（含确认对话框，支持 ContentResolver + 文件直删双路径）
- **繁体→简体**：所有元数据和歌词自动转换

### 📖 歌词系统

最核心的高级功能之一：

- **三级级联获取**：
  1. **LRCLIB 精确匹配** — 按曲名 + 艺术家 + 专辑
  2. **LRCLIB 模糊搜索** — 关键词匹配
  3. **网易云音乐** — 中文歌曲最佳覆盖
- **Room 数据库持久化** — 一次获取永久保存，冷启动秒级加载
- **高级歌词 UI**（见下方动画系统）

### 🖼️ 封面系统

- **自动生成默认封面** — 根据歌名哈希选择品牌色 + 歌名居中显示的艺术渐变封面
- **写入音频元数据** — 封面嵌入音频文件（直写 + ContentResolver 双策略）
- **持久化** — `filesDir/covers/` + Room DB `artworkUri` 双重保护
- **批量生成** — 设置页一键为所有歌曲生成默认封面（带进度条，OOM 保护）
- **缺失自动补全** — 启动时自动为无封面歌曲生成
- **外部封面选择** — 编辑元数据时可从系统相册选择自定义封面

### ▶️ 播放控制

- 播放 / 暂停（Crossfade 动画切换）
- 上一首 / 下一首
- 进度拖动（Material Design 3 Slider）
- 三种循环模式：顺序 / 单曲 / 全部
- 随机播放
- **睡眠定时器**：支持按分钟计时 / 当前曲目结束停止

### 🎬 动画系统（MD3 规范）

| 动画 | 实现 | 评分 |
|:---|:-----|:----:|
| **歌词行过渡** | 7 路 `animateFloatAsState` + spring | ⭐⭐⭐⭐⭐ |
| **Karaoke 填充** | Canvas 从左到右扫描线 + lerp 颜色渐变 | ⭐⭐⭐⭐⭐ |
| **呼吸脉冲边条** | `rememberInfiniteTransition` 无限循环 pulse | ⭐⭐⭐⭐⭐ |
| **点击回弹** | `animateFloatAsState` 0.94 → 1f spring bounce | ⭐⭐⭐⭐ |
| **字体缩放动画** | 非当前行 scale 0.92、字号 16→24sp、weight 400→800 | ⭐⭐⭐⭐⭐ |
| **字间距动画** | 当前行 letterSpacing 0.5→2sp | ⭐⭐⭐⭐ |
| **自动滚动** | `animateScrollToItem` 居中算法 + 手动拖拽 1.5s 后恢复 | ⭐⭐⭐⭐⭐ |
| **MiniPlayer 进出** | slideInVertically + spring bounce | ⭐⭐⭐⭐ |
| **播放/暂停切换** | Crossfade + 200ms tween | ⭐⭐⭐⭐ |
| **页面过渡** | M3 FadeThrough + Emphasized easing | ⭐⭐⭐⭐ |
| **导航栏切换** | animateColor 颜色过渡 | ⭐⭐⭐ |

### 🎛️ 歌词时间轴偏移

- 每首歌独立保存偏移量
- 精细调节：-1s / -0.5s / -0.1s | 重置 | +0.1s / +0.5s / +1s
- 偏移量范围 ±10s
- Room 数据库持久化 — 调一次永久生效
- **默认收纳** — 折叠成「调▾」指示灯，点击展开控制面板

### 📋 曲库浏览

- 三段式切换：**歌曲** / **专辑** / **艺术家**
- MD3 SegmentedButton 控件
- 多种排序方式（按标题/艺术家/专辑/时长/添加时间）
- 实时搜索（展开式搜索栏，动画进出）
- 专辑网格视图（2列 LazyVerticalGrid）
- 专辑/艺术家歌曲列表（ModalBottomSheet）
- 底部 MiniPlayer 始终可见

### ⚙️ 设置页面

- **深色主题**切换
- **全盘扫描 / 指定文件夹扫描**
- **统计面板**：歌曲数 / 专辑 / 艺术家 / 总时长 / 存储占用
- **封面管理**：一键全部生成默认封面（带进度条）
- **支持格式**展示
- **关于**：版本号、设备信息

---

## 🔧 技术栈

| 层 | 技术 |
|:---|:-----|
| 语言 | **Kotlin** 2.1.0 |
| UI 框架 | **Jetpack Compose** + Material Design 3 |
| 播放引擎 | **Media3** ExoPlayer 1.9.3 |
| 数据库 | **Room** 2.6.1 |
| 导航 | **Navigation Compose** |
| 图片加载 | **Coil** |
| 元数据标记 | **jaudiotagger** |
| 动画 | Compose Animation API（spring / tween / infiniteRepeatable） |
| 构建 | **Gradle** + Kotlin DSL + Version Catalog |
| API | LRCLIB + Netease Cloud Music + MusicBrainz |
| 最低 SDK | **API 28** (Android 9.0) |
| 目标 SDK | **API 35** (Android 15) |

---

## 🎯 架构

```
muse/
├── app/
│   └── src/main/
│       ├── java/luzzr/muse/
│       │   ├── data/
│       │   │   ├── database/      # Room 实体、DAO、迁移
│       │   │   ├── model/         # Song / Album / Artist 数据模型
│       │   │   ├── network/       # LRCLIB / 网易云 / MusicBrainz 客户端
│       │   │   ├── repository/    # MusicRepository（核心数据层）
│       │   │   └── tag/           # TagEditor + DefaultCoverGenerator
│       │   ├── player/            # MusicService + PlayerState + SleepTimer
│       │   └── ui/
│       │       ├── animation/     # MotionDuration / Easing / Tween 规格
│       │       ├── components/    # MiniPlayer / LyricsView / FormatBadge
│       │       ├── haptic/        # HapticUtil + PressScale
│       │       ├── navigation/    # NavGraph
│       │       ├── screens/       # Home / Player / Library / Settings
│       │       └── theme/         # Color / Type / Theme MD3 配色
│       └── res/                   # 资源文件（图标、字符串）
├── docs/                          # 开发文档、设计规格
├── soul.md                        # 项目哲学
├── PROJECT.md                     # 项目元数据
└── .hermes-audit-standards.md     # 审核标准
```

---

## 🚀 快速开始

### 环境要求

- Android Studio Ladybug (2024.2+) 或 IntelliJ IDEA
- JDK 17+
- Android SDK 35
- Android 9.0+ (API 28) 设备或模拟器

### 编译

```bash
# 克隆仓库
git clone https://github.com/ninrry/muse.git
cd muse

# 配置签名（发布版需要）
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties 填入你的密钥库信息

# 编译 Debug APK
./gradlew assembleDebug

# 编译 Release APK（需要签名配置）
./gradlew assembleRelease
```

### 安装

```bash
# 通过 ADB 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

或者将 Release APK 传输到手机后手动安装。

### 首次使用

1. 安装后打开 App
2. 授予文件读取权限（Android 13+ 会请求 `READ_MEDIA_AUDIO` 精确权限）
3. 等待自动扫描完成（或去设置中指定文件夹）
4. 开始享受你的音乐！🎶

---

## 📜 歌词源

歌词通过三级级联搜索获取：

| 优先级 | 源 | 匹配方式 | 最佳覆盖 |
|:------:|:---|:---------|:---------|
| ① | **LRCLIB** | 精确匹配（曲名 + 艺术家 + 专辑） | 英文/国际歌曲 |
| ② | **LRCLIB** | 模糊搜索（关键词） | 通用 |
| ③ | **网易云音乐** | API 搜索（`music.163.com/api`） | 中文歌曲 |

所有歌词自动繁体 → 简体转换。

---

## 🖼️ 截图

> *截图正在路上，即将奉上～*

---

## 📝 版本历史

### v1.0.0 (2026-05-13)

- 🎵 完整播放引擎（Media3 ExoPlayer）
- 🎨 MD3 米色系主题（亮/暗）
- 📖 歌词系统（LRCLIB + 网易云级联）
- 🎴 默认封面自动生成 + 持久化
- ⏱️ 歌词时间偏移调节
- 📂 全盘 + 指定文件夹扫描
- 🏷️ 元数据编辑 + 自动搜索
- 📋 曲库分类（歌曲/专辑/艺术家）
- ⏰ 睡眠定时器
- 🔄 播放模式（随机/循环/单曲）
- 🎬 MD3 动画系统（7 层动画规范）
- 🔧 代码质量：P0/P1 问题全修复

---

## 📄 许可证

MIT License — 详见 [LICENSE](LICENSE) 文件。

---

## 💌 致谢

- 项目灵感来源于对本地音乐播放的热爱
- 感谢所有开源组件和它们的维护者
- 特别感谢 [LRCLIB](https://lrclib.net) 和 [MusicBrainz](https://musicbrainz.org) 提供的 API 服务

---

<p align="center">
  <em>Made with ❤️ by 季札</em>
</p>
