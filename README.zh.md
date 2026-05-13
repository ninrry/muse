# Muse 🎵

**安静、克制、温暖**的 Android 本地音乐播放器。

> Muse 不是"能用的播放器"。它是**数字唱片馆**——每个像素、每次交互、每帧动画都在传递"安静、克制、温暖"的情绪。

---

## 功能特色

### 🎵 全格式支持
| 格式 | 类型 |
|:----|:----|
| MP3, AAC, OGG (Vorbis) | 有损压缩 |
| FLAC, ALAC, WAV, AIFF | 无损 |
| **Opus**, **MP4A** (AAC/ALAC) | 现代格式 |
| APE (Monkey's Audio), DSD | 高清无损 |
| AC-3, E-AC-3 (Dolby Digital) | 环绕声 |

### 🎨 Material Design 3
- **暖米色系**调色板，亮度/暗色双主题
- Material You 动态颜色支持
- 圆润克制、安静舒适的视觉语言

### 📂 智能扫描
- **全盘自动扫描**：启动时扫描所有音乐文件
- **指定文件夹扫描**：在设置中自定义路径
- 扫描统计：歌曲/专辑/艺术家数量一目了然

### 🏷️ 元数据解析
- 标题、艺术家、专辑、编码格式、码率、采样率、位深、声道、时长
- 自动提取嵌入封面
- 自动繁体→简体中文转换

### 📋 曲库管理
- 歌曲列表 / 专辑视图 / 艺术家分类
- 全局搜索（按歌名/艺术家/专辑）
- 内嵌封面 + 自动生成默认封面

### ✏️ 文件管理
- **重命名**：歌曲长按 → 重命名
- **删除**：歌曲长按 → 删除（含确认对话框）

### ▶️ 播放体验
- Media3 ExoPlayer 引擎
- 播放/暂停、上一首/下一首、进度拖动
- 顺序循环 / 单曲循环 / 随机播放
- **睡眠定时器**：按时间或当前曲目结束时停止
- **迷你播放器**：随时返回播放控制

### 📜 歌词系统
- **自动获取**：三级级联搜索（LRCLIB精确 → LRCLIB模糊 → 网易云音乐）
- **时间轴偏移调节**：-1s/-0.5s/-0.1s | 重置 | +0.1s/+0.5s/+1s
- **高级 UI**：Karaoke 填充动画、呼吸脉冲边条、弹簧自动滚动、点击回弹
- **繁体→简体**：所有歌词自动转换
- **永久保存**：一次获取，存入 Room 数据库，冷启动自动加载
- **歌词偏移持久化**：每首歌独立保存偏移量，调一次永久生效

### 🎴 封面生成
- 根据歌曲标题自动生成艺术封面
- 写入音频文件元数据 + 本地缓存
- Room 数据库记录封面路径，持久化

---

## 截图

*(即将奉上)*

---

## 快速开始

### 环境要求

- Android Studio Ladybug (2024.2+) 或 IntelliJ IDEA
- JDK 17+
- Android SDK 35
- Android 9.0+ (API 28) 设备

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
adb install app/build/outputs/apk/debug/app-debug.apk
```

或者将 Release APK 传输到手机后手动安装。

### 首次使用

1. 安装后打开 App
2. 授予文件读取权限
3. 等待自动扫描完成（或去设置中指定文件夹）
4. 开始享受你的音乐！🎶

---

## 项目结构

```
muse/
├── app/
│   └── src/main/
│       ├── java/luzzr/muse/
│       │   ├── data/
│       │   │   ├── database/      # Room 实体、DAO
│       │   │   ├── model/         # 数据模型
│       │   │   ├── network/       # API 客户端、解析器
│       │   │   └── repository/    # MusicRepository
│       │   ├── player/            # MusicService、PlayerState
│       │   └── ui/
│       │       ├── animation/     # 动画规格
│       │       ├── components/    # 共享 UI 组件
│       │       └── screens/       # 页面：播放、曲库、设置、首页
│       └── res/                   # 资源文件
├── docs/                          # 开发文档
├── build.gradle.kts               # 根构建脚本
├── soul.md                        # 项目哲学
└── PROJECT.md                     # 项目元数据
```

---

## 歌词源

歌词通过三级级联搜索获取：

1. **LRCLIB**（精确匹配：曲名 + 艺术家 + 专辑）
2. **LRCLIB**（模糊搜索）
3. **网易云音乐**（中文歌曲最佳覆盖，通过 `music.163.com/api`）

所有歌词自动繁体→简体转换。

---

## 技术栈

| 层 | 技术 |
|:---|:-----|
| 语言 | **Kotlin** 2.1.0 |
| UI | **Jetpack Compose** + Material Design 3 |
| 播放引擎 | **Media3** ExoPlayer 1.9.3 |
| 数据库 | **Room** 2.6.1 |
| DI | 手动（无框架依赖） |
| 导航 | **Navigation Compose** |
| 图片加载 | **Coil** |
| 元数据标记 | **jaudiotagger** |
| 构建 | **Gradle** + Kotlin DSL + Version Catalog |

---

## 许可证

MIT License — 详见 [LICENSE](LICENSE) 文件。

---

## 致谢

- 项目灵感来源于对本地音乐播放的热爱
- 感谢所有开源组件和它们的维护者

---

*Made with ❤️ by 季札*
