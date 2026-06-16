# Muse

Muse 是一款面向中文用户的 Android 本地音频播放器，重点支持音乐、有声书、歌词和可持久保存的音频元数据编辑。

它适合长期保存本地音频文件的用户：当你修改歌名、歌手、专辑、年份、流派或封面时，Muse 会尽量把这些信息写回音频源文件，而不是只写到应用自己的数据库里。这样即使卸载重装、重新扫描媒体库，或换其他播放器播放，已写入的元数据仍然可以保留。

[下载最新版](https://github.com/ninrry/muse/releases/latest) |
[下载 2.0 版安装包](https://github.com/ninrry/muse/releases/download/v2.0/2.0.apk) |
[查看更新记录](CHANGELOG.md) |
[查看路线图](docs/ROADMAP.md)

## 主要功能

- 扫描本地音乐和有声书音频文件。
- 使用 Android Media3 播放内核，支持通知栏和系统媒体控制。
- 自动区分音乐和有声书音频。
- 保存有声书播放进度，支持章节级断点续听。
- 搜索、显示和手动校正 LRC 歌词。
- 从多个来源搜索歌曲元数据和封面候选项。
- 编辑歌名、歌手、专辑、年份、流派和封面。
- 将支持的元数据物理写入音频源文件。
- 将应用内缓存与音频文件内嵌元数据保持同步。

## 2.0 版重点

2.0 版主要解决元数据持久化、封面写入和搜索质量问题。

- 歌曲和有声书均支持物理写入元数据。
- 支持将封面写入 MP3、FLAC、OGG/Opus、M4A、M4B、MP4/ALAC、WAV 等主流音频路径。
- 为 M4A、M4B、MP4、ALAC 增加更安全的原子级元数据写入。
- 为 OGG/Opus 增加有声书常用标签和封面写入支持。
- 写入封面前自动压缩和归一化图片，减少大图导致的卡顿和长时间等待。
- 扩充元数据和封面来源，包含 MusicBrainz、Cover Art Archive、网易云、iTunes、Deezer、QQMusic 等。
- 优化搜索排序，提高原唱、原专辑和高质量封面结果的命中率。
- 文件元数据更新后，播放器和系统媒体信息会同步刷新。

## 支持的音频元数据

| 格式 | 文本标签 | 内嵌封面 | 说明 |
| --- | --- | --- | --- |
| MP3 | 支持 | 支持 | 使用 ID3 标签 |
| FLAC | 支持 | 支持 | 使用 FLAC/Vorbis 标签 |
| OGG / Opus | 支持 | 支持 | 使用 OpusTags |
| M4A / M4B / MP4 / ALAC | 支持 | 支持 | 使用 MP4 原子写入 |
| WAV | 支持 | 尽力支持 | 取决于文件标签结构 |

对于不支持、损坏或权限不足的文件，Muse 会返回明确错误，不会假装写入成功。

## 下载

当前版本发布在 GitHub 发布页：

- [2.0 版发布页](https://github.com/ninrry/muse/releases/tag/v2.0)
- [直接下载安装包](https://github.com/ninrry/muse/releases/download/v2.0/2.0.apk)

系统要求：Android 9.0 或更高版本。

## 权限说明

Muse 需要存储权限来扫描和修改本地音频文件。对于较新的 Android 系统，如果音频文件位于受限制目录，物理写入可能需要完整文件访问权限，或通过 Shizuku 等方式获得更高文件写入能力。

如果无法直接写入文件，Muse 会显示权限或文件错误，不会只做应用内软写入。

## 项目结构

```text
app/                         应用入口、导航、依赖注入和应用级用例
core/common/                 通用工具和结果类型
core/model/                  领域模型
core/domain/                 仓库契约和用例
core/data/                   扫描器、仓库实现、元数据写入器和标签解析器
core/database/               Room 数据库、DAO 和迁移
core/network/                元数据、封面和歌词网络客户端
core/media/                  Media3 播放器和服务集成
core/designsystem/           主题、排版、颜色和设计基础组件
core/ui/                     通用 Compose 界面组件
feature/home/                首页
feature/library/             曲库和元数据编辑
feature/audiobook/           有声书和书籍合集
feature/player/              播放器和歌词面板
feature/settings/            设置
benchmark/                   性能测试入口
baselineprofile/             基线配置生成
build-logic/                 Gradle 约定插件
```

项目采用清洁架构风格的多模块结构：功能模块依赖领域契约，数据模块实现这些契约，应用模块通过 Hilt 组装依赖图。

## 本地构建

环境要求：

- JDK 17
- Android Studio 或 Android SDK 命令行工具
- Android SDK 36

克隆并构建调试包：

```bash
git clone https://github.com/ninrry/muse.git
cd muse
./gradlew assembleDebug
```

安装调试包：

```bash
./gradlew installDebug
```

构建签名发布包：

```bash
./gradlew assembleRelease
```

发布签名读取仓库根目录的 `keystore.properties`。该文件已被忽略，不应提交到仓库。

```properties
keystorePath=../你的签名文件.keystore
keystorePwd=你的签名文件密码
keyAliasName=你的密钥别名
keyPwd=你的密钥密码
```

发布包生成位置：

```text
app/build/outputs/apk/release/app-release.apk
```

## 本地检查

常用检查命令：

```bash
./gradlew :app:compileDebugKotlin
./gradlew :core:data:testDebugUnitTest
./gradlew testDebugUnitTest
./gradlew lint
```

发布前验证：

```bash
./gradlew :app:assembleRelease --stacktrace
```

## 发布流程

Android 版本号定义在 [app/build.gradle.kts](app/build.gradle.kts)：

```kotlin
versionCode = 20
versionName = "2.0"
```

构建签名安装包后创建 GitHub 发布：

```bash
gh release create v2.0 \
  app/build/outputs/apk/release/app-release.apk#2.0.apk \
  --title "版本 2.0" \
  --latest
```

## 文档

- [架构说明](docs/ARCHITECTURE.md)
- [质量标准](docs/QUALITY.md)
- [发布流程](docs/RELEASE.md)
- [路线图](docs/ROADMAP.md)
- [更新记录](CHANGELOG.md)
- [贡献指南](CONTRIBUTING.md)
- [安全说明](SECURITY.md)

## 开源许可

Muse 基于 [MIT 许可证](LICENSE) 开源。
