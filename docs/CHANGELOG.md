# 更新日志

本文档记录 Muse 音乐播放器的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [未发布]

### 新增

- Timber 结构化日志系统 (`MuseLog`) 替代 `android.util.Log`
- `ErrorMapper` 错误映射器，将异常转换为用户友好的错误消息
- 新增 `AppException` 子类型：`PermissionDeniedException`、`NotFoundException`、`CancelledException`
- GitHub Actions CI 流水线：lint、单元测试、Android 测试、Debug APK 构建
- ktlint 和 detekt 代码检查插件
- WorkManager 后台任务支持
- Hilt-Work 集成

### 变更

- `gradle.properties` 启用构建缓存，锁定 ktlint 版本 1.5.0
- `app/build.gradle.kts` 添加 Timber、WorkManager、Robolectric 等依赖

### 修复

- 修复歌词同步偏移量计算错误
- 修复部分设备上封面加载失败的问题

## [1.1.0] - 2025-05-XX

### 新增

- 初始公开版本
- 本地音频播放功能
- 元数据搜索（MusicBrainz + Deezer）
- 歌词搜索（LRCLIB + Netease）
- 默认封面生成
- 睡眠定时器
- 播放队列管理
- 歌词同步显示
- 元数据编辑
- 专辑/艺术家浏览

### 技术栈

- Jetpack Compose UI 框架
- Media3 (ExoPlayer) 媒体播放
- Hilt 依赖注入
- Room 数据库
- Kotlin Coroutines & Flow 异步编程
- Coil 图片加载

## [1.0.0] - 2025-XX-XX

### 新增

- 项目初始化
- 基础架构搭建
- 核心播放功能

---

## 版本说明

### 版本号规则

- **主版本号 (MAJOR)**: 不兼容的 API 变更
- **次版本号 (MINOR)**: 向后兼容的功能性新增
- **修订号 (PATCH)**: 向后兼容的问题修正

### 变更类型

- **新增 (Added)**: 新功能
- **变更 (Changed)**: 现有功能的变更
- **弃用 (Deprecated)**: 即将移除的功能
- **移除 (Removed)**: 已移除的功能
- **修复 (Fixed)**: Bug 修复
- **安全 (Security)**: 安全相关的变更

## 升级指南

### 从 1.0.x 升级到 1.1.0

1. 数据库会自动迁移，无需手动操作
2. 新增权限：`INTERNET`（用于歌词获取）
3. 最低 SDK 版本提升至 28 (Android 9)

### 从 1.1.0 升级到未发布版本

1. 检查 `keystore.properties` 配置
2. 更新 Gradle 到最新版本
3. 运行 `./gradlew clean build` 确保构建成功

## 贡献者

感谢所有为 Muse 做出贡献的开发者！

<!-- 贡献者列表 -->

---

*最后更新：2025年*
