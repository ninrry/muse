# 架构说明

## 当前结构

当前版本已建立 `build-logic` 约定插件，完成 `core:common`、`core:model`、`core:domain`、`core:database`、`core:network`、`core:data`、`core:media`、`core:designsystem` 与 `core:ui`，并建立 `feature:home`、`feature:library`、`feature:audiobook`、`feature:player`、`feature:settings`、`benchmark` 与 `baselineprofile` 模块。`app` 现在负责应用入口、导航组合、Hilt 绑定、后台任务调度、Android 权限 / 系统能力适配与应用级状态装配。迁移期间遵循以下依赖方向：

```text
feature UI -> core:ui/designsystem + core:domain/model + core:media API
core:ui -> core:designsystem + core:model
core:data -> core:domain/model + core:database/network/common
core:media -> core:domain/model/common
app/di/work -> 组合具体实现
```

- `core:common` 提供类型化 `OperationResult` 与跨模块日志抽象；`core:model` 是唯一领域模型来源；`core:domain` 暴露仓库契约、歌词 / 元数据搜索端口、主题偏好端口、扫描历史 / 扫描控制端口、默认封面生成端口、领域文本标准化接口与纯 LRC 解析器。
- `core:database` 持有 Room 实体、DAO、数据库和 1→4 Schema；`core:network` 持有歌词 / 元数据网络实现；`core:data` 持有仓库实现、扫描器、封面生成与标签写入。
- `core:media` 暴露 `PlaybackController`、`PlaybackActionController`、`PlaybackServiceStarter`、不可变 `PlaybackState` 与类型化播放模式；播放器服务实现已迁入该模块，UI 和 app 用例不再直接引用 `MusicService` 或操作 `PlayerState`。
- `core:designsystem` 持有颜色、排版、形状、间距、窗口尺寸、动效与触觉 token；`core:ui` 持有迷你播放器、歌曲行、歌词视图、格式徽章等可复用 Compose 组件，以及通用 `UiText`、错误映射、歌词状态和存储权限 UI 端口。
- `feature:home` 已承载首页 Route、ViewModel、Screen、UI state、首页文案与 ViewModel 单测；扫描通过领域扫描控制端口进入，播放动作通过 media API 进入，不再依赖 app 私有用例或扫描实现。
- `feature:library` 已承载曲库 Route、ViewModel、Screen、Dialog、列表 / 详情组件、曲库 / 元数据文案与 ViewModel 单测；元数据搜索历史、存储权限恢复和通用错误映射分别通过 domain / core:ui 端口进入，不再直接依赖 app 私有状态或 Android `Application`。
- `feature:audiobook` 已承载有声书 Route、ViewModel、Screen、Dialog、卡片 / 列表项组件、有声书文案与 ViewModel 单测；合集写入通过领域仓库契约进入，播放动作通过 media API 进入，不再依赖 app 私有用例。
- `feature:player` 已承载完整播放器 Route、ViewModel、Screen、歌词面板、封面区、控制区、队列底栏、睡眠定时弹窗、播放器文案与 ViewModel 单测；歌词状态和会话恢复通过 `core:ui` 端口进入，播放控制通过 media API 进入，由 app/Hilt 组合具体实现。
- `feature:settings` 已承载设置页 Route、ViewModel、Screen、设置小组件、设置文案与 ViewModel 单测；状态和操作通过主题偏好、扫描控制与默认封面生成领域端口接入，由 app/Hilt 组合具体实现。设置 ViewModel 不再直接依赖 Android `Application`、app 私有 `ThemeManager`、仓库实现、播放器状态、扫描用例或 `SharedPreferences`。
- 曲库 UI 已移除对元数据网络实现、歌词缓存实现、默认封面生成实现和存储权限启动细节的直接依赖；元数据搜索、歌词搜索 / 缓存、文本标准化、默认封面预览和权限恢复均通过用例或领域 / UI 接口进入。
- `MediaClassifier` 是音乐 / 有声书分类的唯一入口，OGG 统一视为有声书。
- `benchmark` 与 `baselineprofile` 共享关键用户旅程；Profile 自动打包进 Release，宏基准使用接近 Release 的独立变体。
- Room 数据库名称与 1→4 Schema 保持稳定；迁移必须显式、可测试且无破坏性回退。
- 播放、扫描、歌词和后台任务使用 Flow 暴露状态；UI 与用例仅依赖 `PlaybackController`、`PlaybackActionController` 和 `PlaybackServiceStarter` 等 media API；UI 副作用通过独立 Effect 流处理。
- 文件元数据写入必须先检查能力、保留原文件、写后验证，并仅在成功后提交数据库；数据库提交失败时要回滚文件，自动元数据套用失败时不得继续清歌词、下载封面或关闭结果面板。

## 目标模块

目标结构为 `app`、`core:model/common/domain/database/network/data/media/designsystem/ui`、五个 `feature:*`、`benchmark` 与 `baselineprofile`。

迁移顺序固定为：

1. 纯领域模型与公共接口：已完成。
2. database、network、data、media 实现：已完成。
3. designsystem 与公共 UI：基础已完成，继续补截图回归。
4. 逐个 feature：`feature:home`、`feature:library`、`feature:audiobook`、`feature:player` 与 `feature:settings` 已迁入 Route/ViewModel。
5. benchmark 与 baselineprofile：已建立，持续补旅程。

Feature 禁止互相直接依赖，database/network/data 实现不得泄漏到 UI。每一步必须保持构建、迁移测试和核心播放流程绿色。
