# Changelog

---

## [Unreleased]

_当前无待发布内容。_

---

## [2.0] — 2026-07-13

### Added
- Shizuku 集成支持特权文件操作
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
- Shizuku 路径验证
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