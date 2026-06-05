<div align="center">

# 🎵 Muse

**专为中文用户打造的 Android 本地音频播放器**  
音乐与有声书同等核心，暖棕米色 × 深夜唱片馆双主题

[![CI](https://github.com/ninrry/muse/actions/workflows/ci.yml/badge.svg)](https://github.com/ninrry/muse/actions/workflows/ci.yml)
[![Release](https://github.com/ninrry/muse/actions/workflows/release.yml/badge.svg)](https://github.com/ninrry/muse/actions/workflows/release.yml)
[![Latest Release](https://img.shields.io/github/v/release/ninrry/muse?include_prereleases&label=latest)](https://github.com/ninrry/muse/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-28-brightgreen)](https://developer.android.com/about/versions/pie)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-blue.svg?logo=kotlin)](https://kotlinlang.org)

[下载最新版](https://github.com/ninrry/muse/releases/latest) · [查看变更记录](CHANGELOG.md) · [路线图](docs/ROADMAP.md)

</div>

---

## ✨ 功能特性

| 模块 | 功能 |
|------|------|
| 🎵 **音乐播放** | 本地扫描、队列管理、顺序 / 随机 / 单曲循环播放模式 |
| 📖 **有声书** | OGG 文件自动归类、书籍合集管理、章节进度持久化 |
| 🎤 **歌词** | LRC 滚动歌词、时间轴手动校正、QQMusic 在线搜索 |
| 🖼️ **封面** | 内嵌封面读取、网络封面搜索（QQMusic）、本地封面缓存 |
| ✏️ **元数据编辑** | 标题 / 艺术家 / 专辑 / 年份 / 封面的读写与写后校验 |
| 🌙 **主题** | 暖棕米色（浅）× 深夜唱片馆（深），自动跟随系统深色模式 |
| ⏱️ **睡眠定时** | 完成当前曲目后停止 / 定时停止 |
| 📡 **媒体通知** | Media3 标准通知栏控件，支持系统媒体中心 |
| 💾 **数据安全** | Room 数据库渐进迁移，禁止破坏性清库 |

---

## 📱 快速开始

### 环境要求

- **JDK** 17+
- **Android Studio** Ladybug 或更新版本
- **Android SDK** 36（compileSdk / targetSdk）
- `minSdk` **28**（Android 9.0+）

### 构建运行

```bash
# 克隆仓库
git clone https://github.com/ninrry/muse.git
cd muse

# Debug 构建安装
./gradlew installDebug

# 或仅编译 APK
./gradlew assembleDebug
```

### 签名发布构建

在根目录创建 `keystore.properties`（已被 `.gitignore` 排除）：

```properties
keystorePath=../your.keystore
keystorePwd=your_keystore_password
keyAliasName=your_key_alias
keyPwd=your_key_password
```

```bash
./gradlew assembleRelease
```

---

## 🏗️ 架构

Muse 采用 **Clean Architecture + MVI** 分层，Gradle 多模块拆分：

```
app/                    # 入口、导航、DI 组合、后台调度
├── core/
│   ├── model           # 唯一领域模型
│   ├── domain          # 仓库契约 & 用例
│   ├── data            # 仓库实现、扫描器、标签写入
│   ├── database        # Room 数据库 & DAO
│   ├── network         # 歌词 / 封面网络实现
│   ├── media           # Media3 播放器封装
│   ├── designsystem    # 颜色 / 排版 / 动效 Token
│   └── ui              # 可复用 Compose 组件
└── feature/
    ├── home            # 首页
    ├── library         # 曲库 & 元数据编辑
    ├── audiobook       # 有声书
    ├── player          # 完整播放器 & 歌词面板
    └── settings        # 设置页
```

> 详见 [架构说明](docs/ARCHITECTURE.md)

---

## 🔧 质量检查

```bash
# 格式 & 静态分析
./gradlew ktlintCheck detekt

# 单元测试
./gradlew testDebugUnitTest --max-workers=1

# Android Lint
./gradlew lint --max-workers=1

# 仪器测试（需连接设备或模拟器）
./gradlew :core:database:connectedDebugAndroidTest \
          :app:connectedDebugAndroidTest --max-workers=1
```

> 详见 [质量标准](docs/QUALITY.md)

---

## 🚀 发布流程

推送 `v*` 格式的 Git Tag 即可触发 GitHub Actions 自动发布：

```bash
git tag v1.x.x
git push origin v1.x.x
```

工作流将自动完成 ktlint/detekt 检查、单元测试、签名 Release APK 编译，并上传到 [GitHub Releases](https://github.com/ninrry/muse/releases)，附带 SHA-256 校验和。

> 详见 [发布流程](docs/RELEASE.md)

---

## 📚 文档

| 文档 | 说明 |
|------|------|
| [架构说明](docs/ARCHITECTURE.md) | 模块职责与依赖方向 |
| [质量标准](docs/QUALITY.md) | Lint / Detekt / ktlint 规则与 CI 要求 |
| [发布流程](docs/RELEASE.md) | 签名配置与版本管理 |
| [路线图](docs/ROADMAP.md) | 后续功能规划 |
| [变更记录](CHANGELOG.md) | 各版本变更历史 |

---

## 🤝 参与贡献

欢迎提交 Issue 和 Pull Request！请先阅读 [贡献指南](CONTRIBUTING.md)。

---

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。  
Copyright © 2026 季札
