# 路线图

## 已完成：稳定性基线

- 修复播放重复模式、通知动作、WorkManager、OGG 分类和 UI 副作用缺陷。
- 恢复 JVM 与 Android 仪器测试。
- 清零代码级 Detekt 与 Lint 问题，恢复严格门禁。
- 升级至 API 36 兼容工具链和稳定 AndroidX / Compose / Media3 / Room / WorkManager。
- 建立 CI、定期测试矩阵、Dependabot 与标签发布流程。

## 下一阶段：模块与公共接口

- 已建立 `build-logic`、`core:common`、`core:model`、`core:domain`、`core:database`、`core:network`、`core:data`、`core:media`、`core:designsystem`、`core:ui`、`feature:home`、`feature:library`、`feature:audiobook`、`feature:player`、`feature:settings`、`benchmark` 与 `baselineprofile`。
- 已统一平台无关领域模型和字符串 URI。
- 已移除 typealias、Repository Delegate 与 `MusicRepositoryFacade`。
- 已引入类型化 `OperationResult`、`PlaybackController`、`PlaybackServiceStarter`、不可变 `PlaybackState` 与类型化重复 / 睡眠定时模式。
- 已迁出 database、network、data、播放器服务实现、核心媒体资源、Design System token、可复用 Compose 组件、首页纯 UI、有声书纯 UI 与设置页纯 UI。
- 已收紧曲库 UI 边界：元数据搜索、歌词搜索 / 缓存、文本标准化、默认封面预览和存储权限恢复不再由曲库 ViewModel 直接访问实现层；歌词与元数据搜索已通过领域端口接入，歌词 LRC 解析器迁入领域层并补领域测试。
- 已将 Home Route/ViewModel、Screen、UI state、文案与 ViewModel 单测迁入 `feature:home`；已将 Library Route/ViewModel、Screen、Dialog、列表 / 详情组件、文案与 ViewModel 单测迁入 `feature:library`；已将 Audiobook Route/ViewModel、Screen、Dialog、卡片 / 列表项组件、文案与 ViewModel 单测迁入 `feature:audiobook`；已将 Player Route/ViewModel、Screen、歌词面板、封面区、控制区、队列底栏、睡眠定时弹窗、播放器文案与 ViewModel 单测迁入 `feature:player`，歌词状态 / 会话恢复通过 `core:ui` 端口进入；播放服务启动与播放动作已通过 media API 抽象；设置页主题偏好、扫描控制、扫描历史和默认封面批量生成后的播放器刷新已抽入可替换边界；`feature:settings` 已迁入 Route/ViewModel 并补 ViewModel 单测。

## 后续阶段：性能与视觉

- 已增加冷启动、四标签和设置滚动 Macrobenchmark 与 Baseline Profile；下一步加入带媒体夹具的播放器、歌词、曲库搜索和有声书合集旅程。
- 优化扫描批处理、图片解码缓存、Room 查询和 Compose 稳定性。
- 继续收敛空状态、权限恢复路径和复杂页面的 feature 内 UI 状态，优先把有声书集合详情与曲库编辑 / 元数据流程拆成更小的 Route/Screen 状态边界。
- 增加关键页面深浅主题、手机/平板与大字体截图回归，并覆盖 `core:ui` 公共组件。
