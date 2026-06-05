# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 规范，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

---

## [Unreleased]

_当前无待发布内容。_

---

## [1.1.8] — 2026-06-05

### Added
- 有声书大类隔离与书籍合集手动排序管理
- 歌词时间轴无限制手动校正（支持正负偏移任意量）
- QQMusic 歌词源集成与封面回退搜索
- 睡眠定时器（完成当前曲目后停止 / 定时停止）
- 悬浮歌词无障碍语义标注
- 周期任务调度器与 API 28 / 35 测试矩阵

### Changed
- 深色模式现在自动跟随系统设置，设置持久化到下次启动
- 播放状态全局更新频率降低，减少不必要重组
- 元数据写入改为先写文件后提交数据库，写后内容校验，失败时回滚源文件
- 周期扫描与默认封面维护任务增加首轮延迟，避免安装后立即抢占资源
- 升级至 compileSdk / targetSdk 36、Kotlin 2.3、Room 2.8、Compose 2026.05、Media3 1.10

### Fixed
- 点击继续播放有时无响应（播放控制协程并发竞态修复）
- 切换歌曲后歌词未及时更新（歌词状态观察链路修复）
- 切换深色模式后重启应用主题恢复浅色（主题持久化修复）
- 手动元数据编辑因缓存文件缺少扩展名和 FUSE renameTo 限制导致失败
- 有声书 HiltViewModel 注入失败导致的闪退
- 封面写入失败时仍更新数据库的问题
- 自动元数据套用失败后继续执行歌词 / 封面副作用的问题

### Removed
- 仓库中被跟踪的 Release APK（改由 CI 自动构建上传）
- 未使用的设置 UI 组件

---

## [1.1.7] — 2026-06-03

### Added
- 播放进度持久化（有声书章节级断点续听）

### Fixed
- 数据库迁移、歌词与播放器仪器测试
- 封面嵌入写入改为注入式 TagEditor，增加源文件备份与写后校验

---

## [1.1.6] — 2026-06-01

### Changed
- 优化播放模式切换逻辑
- 精准歌词滚动居中算法优化

---

## [1.1.5] — 2026-05-30

### Added
- 元数据与封面网络搜索（MusicBrainz / QQMusic）
- 离线封面缓存回退机制

### Fixed
- 物理路径文件读取兜底，解决部分机型 URI 访问失败

---

## [1.1.4] — 2026-05-28

### Changed
- 歌词视图渲染性能优化
- 播放模式 UI 状态修复（循环图标不同步问题）

---

## [1.1.3] — 2026-05-26

### Added
- 完整播放器歌词面板
- 睡眠定时弹窗 UI

### Fixed
- MediaSession 与通知栏播放控制同步问题

---

## [1.1.2] — 2026-05-24

### Fixed
- 歌词滚动位置计算错误
- 切换曲目后歌词列表未重置到顶部

---

## [1.1.1] — 2026-05-22

### Fixed
- 重复通知按钮问题
- 关闭重复模式后仍重播当前曲目
- 失效 Worker 链路导致扫描任务无法重启

---

## [1.1.0] — 2026-05-15

### Added
- 有声书模块（OGG 文件自动归类）
- 四标签底部导航（首页 / 曲库 / 有声书 / 设置）
- 迷你播放器 → 完整播放器导航
- 元数据编辑 Dialog（标题 / 艺术家 / 专辑 / 年份 / 封面）
- Baseline Profile 与 Macrobenchmark 性能基础设施

### Changed
- 统一 MediaClassifier 为音乐 / 有声书的唯一分类入口
- 迁移至 Gradle 多模块架构（build-logic 约定插件）

### Fixed
- 播放进度与分类不一致问题

---

## [1.0.0] — 2026-05-01

### Added
- 本地音乐文件扫描与播放
- Room 数据库（歌曲 / 播放列表）
- Media3 ExoPlayer 集成与媒体通知
- Jetpack Compose UI（暖棕米色主题）
- Hilt 依赖注入
- 基础 CI（ktlint / detekt / 单元测试）

[Unreleased]: https://github.com/ninrry/muse/compare/v1.1.8...HEAD
[1.1.8]: https://github.com/ninrry/muse/compare/v1.1.7...v1.1.8
[1.1.7]: https://github.com/ninrry/muse/compare/v1.1.6...v1.1.7
[1.1.6]: https://github.com/ninrry/muse/compare/v1.1.5...v1.1.6
[1.1.5]: https://github.com/ninrry/muse/compare/v1.1.4...v1.1.5
[1.1.4]: https://github.com/ninrry/muse/compare/v1.1.3...v1.1.4
[1.1.3]: https://github.com/ninrry/muse/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/ninrry/muse/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/ninrry/muse/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/ninrry/muse/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ninrry/muse/releases/tag/v1.0.0
