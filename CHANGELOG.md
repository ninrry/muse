# Changelog

---

## [Unreleased]

_当前无待发布内容。_

---

## [2.4.0] — 2026-07-19

### Added
- 统一歌词时间轴与同步引擎
- 主播放器句级/逐词歌词填色、跟随状态机和点击跳转
- 自绘悬浮歌词、拖动吸附、锁定穿透与位置恢复

### Changed
- 移除 Shizuku 依赖、服务、权限状态和相关 UI
- 元数据改名后立即同步 URI、数据库、内存列表与播放器队列
- 歌词加载支持取消旧请求并防止过期结果覆盖新歌曲

### Fixed
- 修复元数据写入后必须全盘扫描才能播放的问题
- 修复歌词快速切歌、清空状态和无逐词时间轴时的同步问题

---

## [2.0] — 2026-07-13

### Added
- EPUB 有声书元数据解析
- 默认封面自动生成
- 歌词时间轴手动校正
- 睡眠定时器
- 周期任务调度器
- 数据库索引优化
- 歌词偏移量持久化
- 应用内主题切换

### Changed
- 升级至 compileSdk/targetSdk 36, Kotlin 2.3, Room 2.8, Compose 2026.05, Media3 1.10
- PlaybackController 使用不可变 PlaybackState
- 元数据写入先写文件后提交数据库

### Security
- 移除危险权限
- WorkManager 约束和退避策略

### Accessibility
- 专辑封面添加 contentDescription
- IconButton 触摸目标最小 48dp

---

## [1.0.0] — 2026-05-01

### Added
- 本地音乐扫描与播放
- Room 数据库
- Media3 ExoPlayer 集成
- Jetpack Compose UI
- Hilt 依赖注入
- 基础 CI
