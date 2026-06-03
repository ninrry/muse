# Android / Compose 项目系统性工程规范

> 适用于从个人项目升级到可交接、可维护、可测试 Android 产品工程的规范。  
> 核心目标：**功能可持续扩展、代码可维护、行为可测试、数据可恢复、UI 可复用、发布可追踪。**

---

## 0. 总目标

这个项目的目标不是“能跑”，而是达到：

> **功能可持续扩展、代码可维护、行为可测试、数据可恢复、UI 可复用、发布可追踪。**

判断一个模块是否合格，看这几个问题：

1. 新人能不能在 30 分钟内知道它负责什么？
2. 一个功能出问题时，能不能快速定位到 `usecase` / `repository` / `screen`？
3. 修改 UI 时，会不会影响业务逻辑？
4. 数据迁移、备份恢复、权限、通知、后台任务有没有测试？
5. 未来半年继续加功能，会不会越来越难改？

---

## 1. 架构规范

### 1.1 分层结构

推荐保持这种结构：

```text
app/
  MainActivity
  navigation
  di

core/
  common
  ui
  design
  database
  datastore
  backup
  permissions
  notifications
  worker

domain/
  model
  repository
  usecase

data/
  local
  remote
  repositoryImpl

feature/
  habit
  task
  note
  backup
  settings
  statistics
```

原则：

- **UI 不直接访问数据库。**
- **ViewModel 不直接写 SQL。**
- **Repository 不处理页面状态。**
- **UseCase 只表达业务动作。**

推荐调用链：

```text
HabitEditorScreen
        ↓
HabitEditorViewModel
        ↓
UpdateHabitUseCase
        ↓
HabitRepository
        ↓
HabitRepositoryImpl
        ↓
Room DAO
```

---

### 1.2 模块边界

每个 feature 至少包含：

```text
feature/habit/
  HabitRoute.kt
  HabitScreen.kt
  HabitViewModel.kt
  HabitUiState.kt
  HabitUiEvent.kt
  components/
  preview/
```

不要把一个复杂页面全部塞进 `HabitEditorScreen.kt`。

建议标准：

| 类型             | 推荐行数           | 超过后处理               |
| -------------- | --------------:| ------------------- |
| Screen 文件      | 150 - 300 行    | 拆 components        |
| ViewModel      | 150 - 300 行    | 拆 reducer / usecase |
| RepositoryImpl | 200 - 400 行    | 拆 source / mapper   |
| 单个 Kotlin 文件   | 不建议超过 500 行    | 超过 700 行必须重构        |
| 复杂页面           | 不允许一个文件 800+ 行 | 拆分状态、事件、组件、业务       |

如果出现 `TaskEditorScreen.kt 930 行`、`HabitEditorScreen.kt 829 行` 这类文件，它们已经是**维护风险点**，不是简单的“代码多”。

---

## 2. UI / Compose 规范

### 2.1 页面结构

一个页面建议分成四层：

```kotlin
@Composable
fun HabitRoute(
    viewModel: HabitViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HabitScreen(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@Composable
fun HabitScreen(
    state: HabitUiState,
    onEvent: (HabitUiEvent) -> Unit
) {
    HabitContent(...)
}

@Composable
private fun HabitContent(...) {
    ...
}

@Composable
private fun HabitFormSection(...) {
    ...
}
```

规则：

- **Route 负责连接 ViewModel。**
- **Screen 负责页面组合。**
- **Content 负责布局。**
- **Component 负责可复用 UI。**

禁止在 Composable 里直接写复杂业务逻辑。

---

### 2.2 设计 Token 规范

所有颜色、圆角、间距、字体、动画时间都应该通过 token 使用。

不要这样写：

```kotlin
Color(0xFFFFE8A3)
Modifier.padding(13.dp)
RoundedCornerShape(7.dp)
```

应该这样：

```kotlin
MonetColors.backgroundWarm
MonetSpacing.md
MonetShape.card
```

建议建立：

```text
core/design/
  MonetColorTokens.kt
  MotionTokens.kt
  ShapeTokens.kt
  SpacingTokens.kt
  TypographyTokens.kt
```

基础标准示例：

```kotlin
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

object AppShape {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(24.dp)
}
```

---

### 2.3 UI 适配规范

必须检查：

```text
360dp 小屏
390dp 常规手机
600dp 折叠屏 / 小平板
840dp 平板
深色模式
字体缩放 1.3x / 1.5x
横屏
中文长文本
英文长单词
无障碍 TalkBack
```

最低要求：

| 项目                   | 标准          |
| -------------------- | ----------- |
| 触控区域                 | 不小于 48dp    |
| 主要文字                 | 不低于 14sp    |
| 卡片圆角                 | 统一 token    |
| 页面边距                 | 16dp 起      |
| 列表项高度                | 信息密度和可点击性平衡 |
| `contentDescription` | 图标按钮必须补齐    |
| 动画                   | 必须可降级或关闭    |

---

## 3. 状态管理规范

### 3.1 UiState / UiEvent / UiEffect

每个复杂页面都应该有：

```kotlin
data class HabitUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val errorMessage: String? = null,
    val habits: List<HabitUiModel> = emptyList()
)

sealed interface HabitUiEvent {
    data class TitleChanged(val value: String) : HabitUiEvent
    data object SaveClicked : HabitUiEvent
    data object BackClicked : HabitUiEvent
}

sealed interface HabitUiEffect {
    data object NavigateBack : HabitUiEffect
    data class ShowSnackbar(val message: String) : HabitUiEffect
}
```

规则：

- **UiState 表示页面当前状态。**
- **UiEvent 表示用户行为。**
- **UiEffect 表示一次性事件，例如跳转、Toast、Snackbar。**

不要把导航、Toast、数据库操作直接塞在 Composable 里。

---

### 3.2 ViewModel 规范

ViewModel 只负责：

1. 接收事件
2. 调用 usecase
3. 更新 state
4. 发出 effect

示例：

```kotlin
fun onEvent(event: HabitUiEvent) {
    when (event) {
        is HabitUiEvent.TitleChanged -> {
            uiState.update { it.copy(title = event.value) }
        }

        HabitUiEvent.SaveClicked -> {
            saveHabit()
        }

        HabitUiEvent.BackClicked -> {
            sendEffect(HabitUiEffect.NavigateBack)
        }
    }
}
```

禁止：

```kotlin
// 禁止：ViewModel 里写大量数据库拼装逻辑
// 禁止：ViewModel 里直接处理复杂 UI 组件状态
// 禁止：ViewModel 里出现一堆 if-else 控制所有页面细节
```

---

## 4. 业务层规范

### 4.1 UseCase 规范

UseCase 表达一个明确业务动作：

```kotlin
class ObserveTodayOverviewUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val taskRepository: TaskRepository,
    private val noteRepository: NoteRepository
) {
    operator fun invoke(): Flow<TodayOverview> {
        ...
    }
}
```

命名规则：

```text
ObserveXxxUseCase
CreateXxxUseCase
UpdateXxxUseCase
DeleteXxxUseCase
ValidateXxxUseCase
ExportXxxUseCase
ImportXxxUseCase
```

不要出现：

```text
CommonUseCase
MainUseCase
HandleDataUseCase
DoSomethingUseCase
```

这些名字说明边界不清楚。

---

### 4.2 Repository 规范

domain 层只放接口：

```kotlin
interface HabitRepository {
    fun observeHabits(): Flow<List<Habit>>
    suspend fun createHabit(habit: Habit)
    suspend fun updateHabit(habit: Habit)
    suspend fun deleteHabit(id: HabitId)
}
```

data 层放实现：

```kotlin
class HabitRepositoryImpl @Inject constructor(
    private val habitDao: HabitDao
) : HabitRepository {
    ...
}
```

Repository 不应该返回 Room Entity，应该返回 domain model。

```text
Room Entity → Mapper → Domain Model → UiModel
```

---

## 5. 数据库规范

### 5.1 Room 规范

必须有：

```text
Entity
Dao
Database
Migration
Schema export
Mapper
RepositoryImpl
```

每次修改表结构，必须做三件事：

1. 增加 migration
2. 更新 schema
3. 增加 migration test

示例：

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habit ADD COLUMN color TEXT NOT NULL DEFAULT ''")
    }
}
```

禁止直接 fallback destructive migration，除非是 debug 版本。

---

### 5.2 数据模型规范

分清三种模型：

```text
HabitEntity    // 数据库模型
Habit          // 业务模型
HabitUiModel   // 页面展示模型
```

不要一个 model 到处传。

错误做法：

```kotlin
@Composable
fun HabitItem(entity: HabitEntity)
```

正确做法：

```kotlin
@Composable
fun HabitItem(model: HabitUiModel)
```

---

## 6. 备份与恢复规范

备份模块至少要覆盖：

```text
导出
导入
版本号
文件格式
数据校验
Zip Slip 防护
重复导入策略
部分失败处理
恢复前确认
恢复后重启或刷新策略
```

备份文件建议包含：

```text
metadata.json
habits.json
tasks.json
notes.json
settings.json
attachments/
```

`metadata.json` 示例：

```json
{
  "appVersion": "1.4.0",
  "backupVersion": 3,
  "createdAt": "2026-06-02T12:00:00Z",
  "databaseVersion": 8
}
```

导入策略必须明确：

| 场景     | 行为           |
| ------ | ------------ |
| 备份版本太旧 | 提示不兼容        |
| 备份版本较旧 | 执行 migration |
| 当前已有数据 | 询问覆盖 / 合并    |
| 部分数据失败 | 给出失败报告       |
| 文件损坏   | 不修改现有数据库     |

---

## 7. 后台任务 / 权限 / 通知规范

### 7.1 WorkManager 规范

WorkManager 只做适合后台执行的任务：

```text
定期提醒检查
数据同步
备份
清理过期数据
统计刷新
```

每个 Worker 必须满足：

```text
幂等
可重试
失败可记录
不依赖 Activity
不阻塞主线程
有输入参数校验
```

示例：

```kotlin
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderScheduler: ReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            reminderScheduler.refresh()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
```

---

### 7.2 开机启动规范

开机广播里不要做重活。

错误：

```kotlin
onReceive() {
    scanDatabase()
    createAllReminders()
}
```

正确：

```kotlin
onReceive() {
    enqueueReminderRefreshWork()
}
```

开机后只负责丢给 WorkManager，避免 ANR。

---

### 7.3 权限规范

所有权限必须集中管理：

```text
core/permissions/
  PermissionManager.kt
  PermissionState.kt
  PermissionRationale.kt
```

不要每个页面自己随便申请权限。

必须覆盖：

```text
通知权限
精确闹钟权限
电池优化
后台限制
文件访问
```

每个权限要有解释文案，不要只弹系统框。

---

## 8. 测试规范

### 8.1 单元测试

必须覆盖：

```text
UseCase
RepositoryImpl
Mapper
Validator
Backup parser
Import/export
Reminder scheduler
Date/time calculation
```

命名：

```kotlin
@Test
fun `createHabit should reject empty title`() {
    ...
}
```

不要写这种：

```kotlin
@Test
fun test1() {}
```

---

### 8.2 UI 测试

至少覆盖：

```text
首页加载
新增习惯
编辑任务
备份页面
设置页面
空状态
错误状态
```

Compose UI 测试关注：

```text
是否展示正确内容
按钮是否可点击
输入是否生效
错误提示是否出现
```

---

### 8.3 数据库迁移测试

必须有：

```text
1 → 2
2 → 3
3 → 4
旧版本真实数据 → 新版本
```

这是长期维护 Android App 的关键。

---

### 8.4 备份恢复测试

必须覆盖：

```text
正常导出
正常导入
损坏文件
旧版本备份
恶意 zip 路径
重复导入
空数据库导入
已有数据导入
```

---

## 9. 代码质量规范

### 9.1 禁止项

项目中尽量避免：

```text
超大 Composable 文件
Activity 承担业务逻辑
Composable 直接访问 Repository
随处硬编码 String
随处硬编码 Color / dp / sp
没有测试的数据库 migration
没有测试的备份导入
启动时扫描大量数据库
catch Exception 后不处理
GlobalScope
Thread.sleep
magic number
```

---

### 9.2 命名规范

推荐：

```text
HabitRepository
HabitRepositoryImpl
ObserveHabitsUseCase
CreateHabitUseCase
HabitUiState
HabitUiEvent
HabitUiEffect
HabitEditorRoute
HabitEditorScreen
HabitEditorContent
```

避免：

```text
Manager
Helper
Utils
Common
DataHandler
MainLogic
Temp
Test2
NewScreen
```

`Utils` 不是不能有，但它容易成为垃圾桶。能不用就不用。

---

### 9.3 错误处理规范

不要直接：

```kotlin
catch (e: Exception) {
}
```

至少要：

```kotlin
catch (e: IOException) {
    logger.e(e)
    emit(Result.failure(AppError.BackupFailed(e)))
}
```

建议定义统一错误：

```kotlin
sealed interface AppError {
    data class DatabaseError(val cause: Throwable) : AppError
    data class BackupFailed(val cause: Throwable) : AppError
    data class PermissionDenied(val permission: String) : AppError
    data object NetworkUnavailable : AppError
}
```

---

## 10. 文档规范

一个成熟项目至少有这些文档：

```text
README.md
ARCHITECTURE.md
DATABASE.md
BACKUP_FORMAT.md
PERMISSIONS.md
RELEASE.md
TESTING.md
docs/adr/
```

---

### 10.1 README 不应该只写怎么运行

README 至少包括：

```text
项目目标
主要功能
技术栈
模块结构
如何运行
如何测试
如何打包
已知限制
维护说明
```

---

### 10.2 ADR 决策记录

当你做重要工程选择时，写 ADR。

例如：

```text
docs/adr/0001-use-clean-architecture.md
docs/adr/0002-use-workmanager-for-reminders.md
docs/adr/0003-backup-format-v1.md
```

模板：

```markdown
# ADR 0001: 使用 Clean Architecture 分层

## 背景

项目包含习惯、任务、笔记、备份、提醒等复杂功能，直接在 UI 层处理业务会导致维护困难。

## 决策

采用 UI / ViewModel / UseCase / Repository / DataSource 分层。

## 后果

优点：模块边界清晰，便于测试。  
缺点：文件数量增加，需要保持命名规范。
```

---

## 11. 发布规范

### 11.1 Release Checklist

每次发布前必须检查：

```text
./gradlew test
./gradlew lint
./gradlew assembleRelease
Room migration test
备份恢复测试
通知提醒测试
冷启动检查
深色模式检查
小屏检查
无障碍基本检查
版本号更新
CHANGELOG 更新
```

---

### 11.2 版本号规范

建议：

```text
major.minor.patch
```

例如：

```text
1.0.0
1.1.0
1.1.1
```

规则：

| 类型    | 示例         |
| ----- | ---------- |
| patch | 修 bug      |
| minor | 新功能        |
| major | 数据结构或架构大变化 |

---

## 12. CI 规范

最低 CI：

```yaml
check:
  - ktlint
  - detekt
  - unit test
  - lint
  - assemble debug
```

进阶 CI：

```text
release build
baseline profile
screenshot test
dependency check
APK size check
Room schema check
```

个人项目不一定一开始全做，但至少要保证：

```text
push 前能自动跑 test + lint
```

---

## 13. 重构优先级

### 第一优先级：拆大文件

重点处理：

```text
TaskEditorScreen.kt
HabitEditorScreen.kt
BackupRepositoryImpl.kt
```

目标：

```text
Screen 拆成 Route / Screen / Content / Section / Dialog / BottomSheet
RepositoryImpl 拆成 Parser / Writer / Validator / Importer / Exporter
```

---

### 第二优先级：补测试

优先补：

```text
备份导入导出
数据库 migration
提醒调度
启动恢复逻辑
重要 usecase
```

UI 测试可以稍后，数据安全相关测试要先做。

---

### 第三优先级：补文档

先写：

```text
ARCHITECTURE.md
BACKUP_FORMAT.md
TESTING.md
RELEASE.md
```

不是为了好看，是为了以后你自己不忘。

---

### 第四优先级：完善 UI 规范

补齐：

```text
contentDescription
字体缩放
小屏适配
深色模式
横屏
空状态
异常状态
加载状态
```

---

## 14. 评分标准

可以用这个标准自评：

| 项目    | 权重  | 合格标准                         |
| ----- | ---:| ---------------------------- |
| 架构分层  | 15  | UI / domain / data 边界清晰      |
| 可维护性  | 15  | 无大量巨型文件，模块职责明确               |
| 数据安全  | 15  | Room migration、备份恢复可靠        |
| 测试    | 15  | 核心业务、数据、备份有测试                |
| UI 质量 | 10  | token 化、适配、无障碍基础完成           |
| 后台任务  | 10  | WorkManager、通知、权限稳定          |
| 文档    | 10  | 架构、备份、发布、测试有说明               |
| 发布工程  | 10  | CI、lint、release checklist 完整 |

总分解释：

```text
6 分：能用的个人项目
7 分：较强个人项目
8 分：可维护的小团队项目
9 分：接近成熟商业产品工程
10 分：长期团队协作级别
```

---

## 15. 最简执行版

不要一次性全做。建议按这个顺序执行：

```text
1. 拆 TaskEditorScreen / HabitEditorScreen
2. 把所有 raw color / dp / shape 收进 token
3. 给备份导入导出补测试
4. 给 Room migration 补测试
5. 给 WorkManager / 开机恢复逻辑补幂等测试
6. 写 ARCHITECTURE.md
7. 写 BACKUP_FORMAT.md
8. 加 release checklist
9. 加小屏 / 字体缩放 / TalkBack 检查
10. CI 里强制跑 test + lint
```

最核心的一句话：

> **你现在的工程已经有能力感，但需要从“靠作者脑子维护”升级到“靠规范、测试、文档维护”。**
