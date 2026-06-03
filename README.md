# Muse 音乐播放器

Muse 是一款采用 Android 现代技术栈（Jetpack Compose + Hilt + Media3 + Room）构建的本地音乐播放器，旨在提供纯净、快速的本地音乐播放与管理体验。

## 🎯 项目目标
- 提供稳定、轻量的本地音乐播放体验
- 支持歌词（内置与同步）和封面显示
- 采用最先进的 Android 开发规范与架构体系

## 🛠️ 技术栈
- **UI 框架**: Jetpack Compose
- **媒体播放**: Media3 (ExoPlayer) & MediaSession
- **依赖注入**: Hilt
- **持久化**: Room Database
- **异步编程**: Kotlin Coroutines & Flow
- **图片加载**: Coil

## 📂 模块结构
- `data`: 包含 `database` (Room 实体与 DAO)、`repository` (数据操作，包含歌曲、歌词、封面) 和 `network`。
- `di`: Hilt 依赖注入模块定义。
- `player`: Media3 播放器与前台服务实现 (`MusicService`, `SleepTimer`)。
- `ui`: Jetpack Compose UI 层，包含 `theme`, `components`, `screens`。

## 🚀 如何运行
1. 使用 Android Studio (建议 Koala 及以上版本) 打开根目录。
2. 确保 SDK 35 (Android 15) 已安装。
3. 点击 Run 部署到模拟器或真机。

## 🧪 如何测试
本项目对核心业务逻辑编写了充足的单测：
```bash
# 运行单元测试
./gradlew testDebugUnitTest

# 运行数据库迁移等 Android 测试（需连接设备或模拟器）
./gradlew connectedAndroidTest
```

## 📦 如何打包
```bash
./gradlew assembleRelease
```
_注：需要在根目录提供 `keystore.properties` 文件供 release 构建读取签名。_

## ⚠️ 已知限制
- 目前仅支持本地音乐文件播放，不支持流媒体。
- 歌词缓存可能依赖特定的 ID3 标签读取。
