# Muse 音乐播放器

> 一款采用 Android 现代技术栈构建的本地音乐播放器，提供纯净、快速的本地音乐播放与管理体验。

## 项目简介

Muse 是一款专注于本地音乐播放的 Android 应用，支持多种音频格式的播放、歌词显示、元数据编辑等功能。项目采用 Clean Architecture 架构，代码结构清晰，易于维护和扩展。

### 核心功能

- 本地音乐文件扫描与播放
- 同步歌词显示与编辑
- 音乐元数据搜索与编辑（MusicBrainz + Deezer）
- 歌词自动获取（LRCLIB + Netease）
- 默认封面生成
- 睡眠定时器
- 播放队列管理

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| UI 框架 | Jetpack Compose | BOM 2024.x |
| 媒体播放 | Media3 (ExoPlayer) | 1.5.x |
| 依赖注入 | Hilt | 2.51 |
| 持久化 | Room | 2.6.x |
| 异步编程 | Kotlin Coroutines & Flow | 1.8.x |
| 图片加载 | Coil | 2.7.x |
| 日志 | Timber | 5.0.x |
| 后台任务 | WorkManager | 2.9.x |
| 代码检查 | ktlint + detekt | - |

## 架构概述

项目采用 **Clean Architecture** + **MVVM** 架构模式，分为三层：

```
┌─────────────────────────────────────┐
│          UI Layer (Compose)         │
│   Screens, ViewModels, Components   │
├─────────────────────────────────────┤
│        Domain Layer (UseCases)      │
│      业务逻辑，跨 Repository 协调      │
├─────────────────────────────────────┤
│         Data Layer                  │
│   Repositories, Database, Network   │
└─────────────────────────────────────┘
```

详细架构说明请参考 [ARCHITECTURE.md](./ARCHITECTURE.md)。

## 项目结构

```
app/src/main/java/luzzr/muse/
├── core/                   # 核心工具类
│   ├── error/              # 错误处理
│   └── log/                # 日志工具
├── data/                   # 数据层
│   ├── database/           # Room 实体与 DAO
│   ├── model/              # 领域模型
│   ├── network/            # 网络请求（歌词、元数据）
│   ├── repository/         # 数据仓库
│   ├── scanner/            # 媒体扫描
│   └── tag/                # 标签读写
├── di/                     # Hilt 依赖注入模块
├── domain/                 # 领域层
│   └── usecase/            # 用例
├── player/                 # 播放器相关
├── ui/                     # UI 层
│   ├── animation/          # 动画
│   ├── components/         # 通用组件
│   ├── haptic/             # 触觉反馈
│   ├── modifier/           # 自定义 Modifier
│   ├── navigation/         # 导航
│   ├── screens/            # 页面
│   ├── state/              # UI 状态管理
│   └── theme/              # 主题
└── work/                   # WorkManager 任务
```

## 构建说明

### 环境要求

- Android Studio Koala (2024.1.1) 或更高版本
- JDK 17
- Android SDK 35 (Android 15)
- Gradle 8.x

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/your-org/muse.git

# 进入项目目录
cd muse

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本（需要签名配置）
./gradlew assembleRelease
```

### 签名配置

Release 构建需要在项目根目录创建 `keystore.properties` 文件：

```properties
keystorePath=path/to/your.keystore
keystorePwd=your_store_password
keyAliasName=your_key_alias
keyPwd=your_key_password
```

## 测试说明

### 运行单元测试

```bash
./gradlew testDebugUnitTest
```

### 运行 Android 测试

```bash
./gradlew connectedAndroidTest
```

### 测试覆盖率

项目包含以下测试类型：
- 单元测试：ViewModel、Repository、UseCase、数据模型
- 集成测试：数据库迁移、UI 组件

详细测试说明请参考 [TESTING.md](./TESTING.md)。

## 贡献指南

### 代码规范

- 遵循 Kotlin 官方编码规范
- 使用 ktlint 进行代码格式检查
- 使用 detekt 进行静态代码分析

### 提交规范

提交信息格式：

```
<type>(<scope>): <subject>

<body>

<footer>
```

类型（type）：
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式调整
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建/工具相关

### Pull Request 流程

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/your-feature`)
3. 提交更改
4. 推送到分支 (`git push origin feature/your-feature`)
5. 创建 Pull Request

### 代码审查

- 所有 PR 需要通过 CI 检查（ktlint、detekt、单元测试）
- 至少需要一名维护者审查

## 相关文档

- [架构设计](./ARCHITECTURE.md)
- [数据库设计](./DATABASE.md)
- [测试策略](./TESTING.md)
- [权限处理](./PERMISSIONS.md)
- [更新日志](./CHANGELOG.md)
- [架构决策记录](./adr/)

## 许可证

本项目采用 MIT 许可证，详见 [LICENSE](../LICENSE) 文件。
