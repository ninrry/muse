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
| v2.2.0 | 2026-07-16 | [arm64](https://github.com/ninrry/muse/releases/tag/v2.2.0) |
| v2.1.1 | 2026-07-15 | [arm64](https://github.com/ninrry/muse/releases/tag/v2.1.1) |
| v2.1.0 | 2026-07-13 | [arm64](https://github.com/ninrry/muse/releases/tag/v2.1.0) |
| v2.0 | 2026-07-07 | — |

### v2.2.0 (2026-07-16)
- 修复歌词抓取全面失败（v2.1.1 OkHttp 迁移时丢失 `Dispatchers.IO`，主线程阻塞被 `safeCall` 静默吞掉，所有源返回 null）
- 修复网易云/QQ/酷狗/酷我/ovh 中文源"仅含元数据标签"的 LRC 短路阻断后续源
- 修复酷我歌手字段回退失效（`optString` 非空导致 elvis 永远走不到 `singer`）
- 新增酷狗/酷我歌词源（KugouLyricsSource / KuwoLyricsSource）
- 新增 `HttpEngine` 默认 UA 拦截器（自动补全未传 UA 的请求，酷狗/酷我之前裸发 `okhttp/4.x` 被服务端拒绝）
- 优化歌词行号判定：UI 端 `withFrameNanos` 实时二分查找，行号变化延迟从 50-80ms 降到 ~16ms
- 优化逐字涂色：wall clock 推算 position + 亚字符级 reveal，50ms 上游间隙里每 vsync 帧连续推进（移除 24ms 短外推和 0.0008 抖动门限）
- 优化歌词滚动：100ms 硬跳 `scrollToItem` → 行号变化触发 `animateScrollToItem` 平滑动画
- 优化 Compose 重组：歌词行 alpha/scale `animateFloatAsState` 读取推迟到 `graphicsLayer` lambda，spring 期间不再触发重组
- 优化 `NowPlayingBars`：无限动画值读取从 composition 移到 Canvas draw lambda，消除 60fps 持续重组
- 优化 `DefaultAlbumCover`：`textMeasurer.measure` 用 `remember` 缓存，避免 Canvas 每次重绘都重新测量
- 优化 `AlbumListTab`：移除每项入场淡入，LazyGrid 滚动回收不再触发闪烁
- 新增 `reduceMotion` 系统设置检测（`Settings.Global.ANIMATOR_DURATION_SCALE`），传递到歌词组件
- 修复歌单详情页 TopBar 位置（外层 `padding(innerPadding)` 重复下推状态栏高度，改为只 padding bottom）
- 修复歌单删除后未自动返回上级（`MutableSharedFlow` 加 `extraBufferCapacity = 1`，`NavigateBack` 不再因 collector 未就绪而永久挂起）

### v2.1.1 (2026-07-15)
- 安全：迁移网络层 OkHttp（连接池、超时管理、证书信任）
- 安全：文件名 Unicode NFC 规范化加固
- 安全：日志脱敏，移除完整文件路径输出
- 架构：PlayerState 拆分为 SessionPersistenceManager + FloatingLyricsStateHolder
- 性能：暂停时歌词轮询降频 33ms → 200ms
- 性能：播放进度采样 16ms → 50ms（节省 ~70% CPU）
- 性能：歌词 Canvas 自绘渲染（单层替代双层 Text）
- 性能：缓存并发保护（synchronizedMap + ConcurrentLinkedQueue）
- 精细度：reduceMotion 无障碍适配（跳过 VSYNC 外推和动画过渡）
- 精细度：MiniPlayer 进度条 seek 快速跟随（100ms vs 350ms）
- 可维护性：魔法数字命名常量化（8 个命名常量）
- 可维护性：网络异常统一处理（safeCall 包装器）
- 可维护性：日志级别规范（成功路径 debug，正常流 info）

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