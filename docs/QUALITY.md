# 质量标准

## 合并门禁

每个 Pull Request 必须通过：

- `ktlintCheck`
- `detekt`，零代码异味
- 严格 Android Lint，警告视为错误
- JVM 单测
- API 35 仪器测试
- Debug 与 Release/R8 构建

每周与手动工作流额外运行 API 28 / 35 仪器测试矩阵。
定时、手动与标签发布工作流运行 Baseline Profile / Macrobenchmark 关键旅程并保存 JSON 与 Perfetto traces。

## Lint 策略

代码级 Lint 问题不得压制。仅忽略版本可用性类提示，因为 API 36 / AGP 8.13 / Hilt 2.58 是当前经验证的兼容线；依赖升级由 Dependabot 月度分组 PR 单独验证。

## 数据安全

- 禁止 `fallbackToDestructiveMigration`。
- 新 Schema 必须包含从所有受支持版本到最新版本的迁移测试。
- 元数据写入失败不得破坏原文件或更新数据库状态；文件写入成功但数据库提交失败时必须尝试恢复原文件。
- 自动套用网络元数据失败后，不得继续清歌词缓存、下载封面或关闭元数据结果面板。
- 权限拒绝、文件不支持、网络失败、IO 失败和未找到应保持可区分。

## 性能与无障碍

- 不在 `Application.onCreate` 执行非必要数据库、扫描或封面工作。
- 高频播放进度不得驱动整个应用 Scaffold 重组。
- 所有触控目标至少 48dp；支持 TalkBack、大字体、系统动画缩放和深浅主题。
- 性能基准建立后，任何场景回退超过 10% 都需要阻止合并或给出明确解释。
- 模拟器宏基准仅验证旅程和趋势；卡顿率、冻结帧与 10% 回退阈值必须在固定物理设备上判定。
