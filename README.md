# Muse

一款面向本地媒体库的 Android 应用：既是音乐播放器，也是一套支持音频与文字同步的有声书阅读器。

Muse 使用 Jetpack Compose 构建界面，以 Media3 驱动后台播放，并通过 ReadAlong 将 EPUB、章节音频和对齐时间轴组织在同一个阅读体验中。

## 功能概览

### 本地音乐

- 扫描设备中的音频文件，并按歌曲、专辑和艺术家整理
- 搜索、排序、批量选择和歌单管理
- 首页每日推荐与最近播放
- 自动读取本地标签和专辑封面
- 编辑标题、艺术家、专辑、年份、流派及封面
- 将修改后的元数据写回音频源文件
- 检测损坏、缺失或不可写的音频文件

### 播放与歌词

- 基于 AndroidX Media3 的后台播放
- 支持系统媒体控件、通知栏和锁屏控制
- 播放队列、随机播放、单曲循环与列表循环
- 自定义时长、快捷预设和“播完本首”睡眠定时
- 从网易云音乐、酷狗音乐和酷我音乐搜索歌词
- 自动匹配歌曲目录及常用歌词目录中的本地 LRC，并在歌名、歌手一致时优先使用
- 支持普通 LRC、逐字歌词与卡拉 OK 填色
- 手动调整歌词时间偏移，点击歌词跳转播放
- 批量抓取曲库歌词

### ReadAlong 同步阅读

- 导入 `.readalong.zip` 或完整 ReadAlong 文件夹
- 组合 EPUB 正文、章节音频和文字对齐时间轴
- 播放时同步高亮当前文字，并自动跟随阅读位置
- 支持章节切换、前后十秒、倍速播放和断点续听
- 提供目录、全文搜索、书签、批注和阅读进度统计
- 支持滚动/分页阅读模式
- 可调整字体、字重、字号、行距、段距和阅读主题
- 没有配套音频的章节仍可作为普通电子书阅读

## 支持的音频格式

| 格式 | 播放 | 标签读写 | 内嵌封面 |
| --- | :---: | :---: | :---: |
| MP3 | ✓ | ✓ | ✓ |
| FLAC | ✓ | ✓ | ✓ |
| OGG / Opus | ✓ | ✓ | ✓ |
| M4A / M4B / MP4 | ✓ | ✓ | ✓ |
| WAV | ✓ | ✓ | — |

实际播放能力还会受到设备系统解码器支持情况的影响。修改文件标签前，建议保留重要音频的备份。

## 系统与权限

- Android 9.0（API 28）及以上
- 扫描音乐需要音频媒体读取权限
- Android 13 及以上需要通知权限，才能完整显示播放通知
- 将元数据写回共享存储中的源文件时，Android 11 及以上可能需要“管理所有文件”权限
- 在线搜索歌词和元数据需要网络连接

应用可以在未授予文件写入权限时正常扫描和播放音乐；只有修改源文件时才需要额外权限。

## 技术栈

| 领域 | 方案 |
| --- | --- |
| 语言 | Kotlin 2.3 |
| UI | Jetpack Compose + Material 3 |
| 播放 | AndroidX Media3 / ExoPlayer |
| 数据 | Room |
| 依赖注入 | Hilt |
| 异步 | Kotlin Coroutines + Flow |
| 图片 | Coil |
| 网络 | OkHttp |
| 后台任务 | WorkManager |
| 构建 | Gradle 9.4.1 + AGP 8.13 |

## 项目结构

```text
Muse
├── app                    # 应用入口、导航、权限和任务调度
├── build-logic            # Android 与代码质量约定插件
├── core
│   ├── common             # 通用基础能力
│   ├── data               # 仓库实现、扫描、标签和 ReadAlong 导入
│   ├── database           # Room 数据库
│   ├── designsystem       # 主题、排版和尺寸
│   ├── domain             # 业务接口与用例
│   ├── media              # 音乐及 ReadAlong 播放服务
│   ├── model              # 领域模型
│   ├── network            # 歌词和元数据网络源
│   └── ui                 # 跨功能复用的 Compose 组件
└── feature
    ├── home               # 首页和歌单
    ├── library            # 曲库、搜索和元数据编辑
    ├── audiobook          # ReadAlong 书架与阅读器
    ├── player             # 播放器、歌词、队列和睡眠定时
    └── settings           # 权限、扫描、外观和媒体诊断
```

模块之间通过领域接口协作，播放、扫描、数据库和界面状态彼此分离，便于单独测试和维护。

## 本地构建

### 环境要求

- JDK 17
- Android SDK 36
- Gradle 9.4.1

仓库中的 Wrapper 分发地址可能依赖原维护环境。若 Wrapper 在你的机器上不可用，请安装 Gradle 9.4.1，并直接使用下面的 `gradle` 命令。

### Debug APK

```bash
gradle :app:assembleDebug
```

构建产物位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release APK

在项目根目录创建不会被 Git 跟踪的 `keystore.properties`：

```properties
keystorePath=D:/keys/muse.jks
keystorePwd=your_store_password
keyAliasName=your_key_alias
keyPwd=your_key_password
```

然后执行：

```bash
gradle :app:assembleRelease
```

签名后的产物位于：

```text
app/build/outputs/apk/release/app-release.apk
```

请勿将签名文件、密码或 `keystore.properties` 提交到版本库。

## 常用检查

```bash
# 单元测试
gradle testDebugUnitTest

# Android Lint
gradle lintDebug

# Kotlin 格式检查
gradle ktlintCheck

# 静态分析
gradle detekt
```

需要运行 Compose 或数据库设备测试时，请连接模拟器或实体设备：

```bash
gradle connectedDebugAndroidTest
```

## 数据说明

- 音乐索引、歌单、播放记录、阅读进度、书签和批注保存在设备本地
- 删除 ReadAlong 书籍时会同时删除其本地副本与阅读进度
- 在线歌词与元数据来自第三方服务，接口可用性可能随服务端变化
- Muse 不附带音乐、有声书或 ReadAlong 内容，相关文件需由用户自行准备

## 许可证

Muse 使用 [MIT License](LICENSE) 开源。
