# Muse

Muse 是一款面向中文用户的 Android 本地音频播放器，重点支持音乐、有声书、歌词和可持久保存的音频元数据编辑。

[下载最新版](https://github.com/ninrry/muse/releases/latest) |
[更新记录](CHANGELOG.md)

## 主要功能

- 扫描本地音乐和有声书音频文件
- 使用 Android Media3 播放内核，支持通知栏和系统媒体控制
- 自动区分音乐和有声书
- 保存有声书播放进度，支持章节级断点续听
- 搜索、显示和手动校正 LRC 歌词
- 从多个来源搜索歌曲元数据和封面
- 编辑歌名、歌手、专辑、年份、流派和封面
- 将元数据物理写入音频源文件

## 支持的音频格式

| 格式 | 文本标签 | 封面 |
| --- | --- | --- |
| MP3 | ✅ | ✅ |
| FLAC | ✅ | ✅ |
| OGG/Opus | ✅ | ✅ |
| M4A/M4B/MP4/ALAC | ✅ | ✅ |
| WAV | ✅ | ⚠️ |

## 项��结构

```
app/                    # 应用入口、导航、DI
core/common/            # 通用工具
core/model/             # 领域模型
core/domain/            # 仓库契约和用例
core/data/              # 扫描器、仓库实现、元数据写入
core/database/          # Room 数据库
core/network/           # 网络客户端
core/media/             # Media3 播放器
core/designsystem/      # 主题、设计组件
core/ui/                # Compose 组件
feature/home/           # 首页
feature/library/        # 曲库
feature/audiobook/      # 有声书
feature/player/         # 播放器
feature/settings/       # 设置
benchmark/              # 性能测试
baselineprofile/        # 基线配置
```

## 本地构建

环境要求：JDK 17, Android SDK 36

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## 版本历史

| 版本 | 日期 | 下载 |
|------|------|------|
| v2.1.0 | 2026-07-13 | [arm64](https://github.com/ninrry/muse/releases/tag/v2.1.0) / [x86_64](https://github.com/ninrry/muse/releases/tag/v2.1.0) |
| v2.0 | 2026-07-07 | — |

### v2.1.0 (2026-07-13)
- 新增歌词渐进填充动画（逐字扫光，深度层叠 ±2 行）
- 新增 MuseBottomSheet / MuseAlertDialog 统一底部抽屉和弹窗组件
- 新增 MiniPlayer 队列导航（player/queue）
- 新增播放器全沉浸模式（隐藏底部导航）
- 新增浮动歌词开关（PlayerTopBar）
- 修复歌词不自动滚动（帧轮询替代 snapshotFlow）
- 修复浮动歌词 Android 14+ FGS 崩溃（移除 startForeground）
- 优化播放进度轮询 250ms → 8ms（120fps 路径）
- 优化 Monet 红色主题（降饱和度 LightError/DarkError）
- 优化分离架构打包（arm64-v8a / x86_64）

### v2.0 (2026-07-07)
- 首个正式发布版本

## 许可

MIT License - 见 [LICENSE](LICENSE)