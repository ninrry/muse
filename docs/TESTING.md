# 测试策略

本文档描述 Muse 音乐播放器的测试策略、测试类型和测试工具。

## 测试金字塔

```
        ┌─────────┐
        │ UI 测试  │  ← 少量，验证关键用户流程
        ├─────────┤
        │ 集成测试  │  ← 中量，验证组件交互
        ├─────────┤
        │ 单元测试  │  ← 大量，验证业务逻辑
        └─────────┘
```

## 测试类型

### 1. 单元测试

位置：`app/src/test/`

运行命令：
```bash
./gradlew testDebugUnitTest
```

#### ViewModel 测试

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: PlayerViewModel
    private val playerControlUseCase = mockk<PlayerControlUseCase>()

    @Before
    fun setup() {
        viewModel = PlayerViewModel(playerControlUseCase)
    }

    @Test
    fun `play pause toggles state`() = runTest {
        every { playerControlUseCase.togglePlayPause() } just Runs

        viewModel.onPlayPause()

        verify { playerControlUseCase.togglePlayPause() }
    }
}
```

#### Repository 测试

```kotlin
class SongRepositoryTest {
    private lateinit var repository: SongRepository
    private val songDao = mockk<SongDao>()
    private val scanner = mockk<MediaStoreScanner>()

    @Before
    fun setup() {
        repository = SongRepository(songDao, scanner, mockk())
    }

    @Test
    fun `scanAll saves to database`() = runTest {
        val songs = listOf(mockk<Song>())
        coEvery { scanner.scanAll() } returns songs
        coEvery { songDao.deleteAll() } just Runs
        coEvery { songDao.insertAll(any()) } just Runs

        repository.scanAll()

        coVerify { songDao.insertAll(any()) }
    }
}
```

#### UseCase 测试

```kotlin
class FetchLyricsUseCaseTest {
    private val lyricsRepository = mockk<LyricsRepository>()
    private lateinit var useCase: FetchLyricsUseCase

    @Before
    fun setup() {
        useCase = FetchLyricsUseCase(lyricsRepository)
    }

    @Test
    fun `invoke returns success when lyrics found`() = runTest {
        val result = mockk<LyricsResult>()
        coEvery { lyricsRepository.fetchLyrics(any(), any(), any()) } returns result

        val actual = useCase(1, "Title", "Artist")

        assertTrue(actual.isSuccess)
        assertEquals(result, actual.getOrNull())
    }
}
```

#### 数据模型测试

```kotlin
class SongTest {
    @Test
    fun `song properties are correct`() {
        val song = Song(
            id = 1,
            title = "Test Song",
            artist = "Test Artist",
            // ...
        )

        assertEquals(1, song.id)
        assertEquals("Test Song", song.title)
    }
}
```

#### 网络解析测试

```kotlin
class LrcParserTest {
    @Test
    fun `parse valid LRC format`() {
        val lrc = """
            [00:12.34]Hello world
            [00:15.67]Second line
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertEquals(2, result.size)
        assertEquals(12340L, result[0].timeMs)
        assertEquals("Hello world", result[0].text)
    }
}
```

### 2. 集成测试

位置：`app/src/androidTest/`

运行命令：
```bash
./gradlew connectedAndroidTest
```

#### 数据库迁移测试

```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MuseDatabase::class.java
    )

    @Test
    fun migrate1To2_lyricsTableCreated() {
        helper.createDatabase("muse_player.db", 1).close()
        val db = helper.runMigrationsAndValidate(
            "muse_player.db", 2, true, MIGRATION_1_2
        )
        db.query("SELECT * FROM lyrics").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }
}
```

#### UI 组件测试

```kotlin
@RunWith(AndroidJUnit4::class)
class MiniPlayerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun miniPlayer_shows_song_title() {
        composeTestRule.setContent {
            MiniPlayer(
                song = Song(title = "Test Song", ...),
                isPlaying = false,
                onPlayPause = {},
                onClick = {}
            )
        }

        composeTestRule
            .onNodeWithText("Test Song")
            .assertIsDisplayed()
    }
}
```

### 3. UI 测试

使用 Compose Testing API 进行 UI 测试。

```kotlin
@RunWith(AndroidJUnit4::class)
class PlayerControlsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playButton_toggles_icon() {
        composeTestRule.setContent {
            PlayerControls(
                isPlaying = false,
                onPlayPause = {},
                onNext = {},
                onPrevious = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Play")
            .assertIsDisplayed()
    }
}
```

## 测试工具

### 依赖项

```kotlin
// 单元测试
testImplementation(libs.junit)
testImplementation(libs.coroutines.test)
testImplementation(libs.turbine)
testImplementation(libs.mockk)
testImplementation(libs.robolectric)
testImplementation(libs.androidx.work.testing)

// Android 测试
androidTestImplementation(libs.androidx.test.junit)
androidTestImplementation(libs.androidx.test.espresso)
androidTestImplementation(libs.compose.ui.test.junit4)
debugImplementation(libs.compose.ui.test.manifest)
```

### 工具说明

| 工具 | 用途 |
|------|------|
| JUnit | 测试框架 |
| MockK | Kotlin Mock 框架 |
| Coroutines Test | 协程测试支持 |
| Turbine | Flow 测试 |
| Robolectric | Android 单元测试 |
| Espresso | Android UI 测试 |
| Compose Test | Compose UI 测试 |

### MainDispatcherRule

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

## 测试覆盖率

### 关键覆盖区域

| 模块 | 目标覆盖率 | 说明 |
|------|-----------|------|
| ViewModel | 80% | 核心业务逻辑 |
| Repository | 90% | 数据层关键路径 |
| UseCase | 85% | 业务规则 |
| 数据模型 | 95% | 简单逻辑 |
| 网络解析 | 90% | 边界情况 |

### 现有测试文件

```
app/src/test/
├── data/
│   ├── model/
│   │   ├── SongTest.kt
│   │   └── SortTypeTest.kt
│   ├── network/
│   │   ├── LyricsFetcherTest.kt
│   │   ├── LrcParserTest.kt
│   │   └── SearchMatchTest.kt
│   └── repository/
│       ├── LyricsRepositoryTest.kt
│       └── SongRepositoryTest.kt
├── player/
│   ├── PlayerStateTest.kt
│   └── SleepTimerTest.kt
└── ui/
    └── screens/
        ├── home/
        │   ├── HomeStatsTest.kt
        │   └── HomeViewModelTest.kt
        └── player/
            └── PlayerViewModelTest.kt

app/src/androidTest/
├── data/
│   └── database/
│       └── MigrationTest.kt
└── ui/
    ├── components/
    │   ├── FormatBadgeTest.kt
    │   ├── LyricsViewTest.kt
    │   └── MiniPlayerTest.kt
    └── screens/
        └── player/
            └── PlayerControlsTest.kt
```

## 测试最佳实践

1. **命名规范**: 使用反引号描述测试场景
2. **AAA 模式**: Arrange-Act-Assert 结构
3. **单一职责**: 每个测试只验证一个行为
4. **隔离性**: 测试之间互不影响
5. **可读性**: 测试即文档

## CI 集成

GitHub Actions 配置自动运行测试：

```yaml
- name: Run Unit Tests
  run: ./gradlew testDebugUnitTest

- name: Run Lint
  run: ./gradlew ktlintCheck detekt
```
