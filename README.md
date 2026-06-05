# Muse

Muse 是一款直装优先的 Android 本地音频播放器，音乐与有声书同等核心。应用使用 Jetpack Compose、Media3、Room、Hilt、WorkManager 与 Kotlin Coroutines 构建，保持暖棕色的米色简约 / 深夜唱片馆视觉风格。

## 产品约定

- 底部导航固定为首页、曲库、有声书、设置四个标签。
- 首页仅展示音乐；完整播放器从迷你播放器进入。
- OGG 文件统一分类为有声书，不提供手动分类。
- `MANAGE_EXTERNAL_STORAGE` 仅在编辑文件元数据时按需申请。
- 中文单语言；所有用户可见文案使用 Android 资源。
- 数据库、播放进度、合集、歌词与用户设置必须可迁移，禁止破坏性清库。

## 环境

- JDK 17
- Android SDK 36
- `minSdk 28` / `targetSdk 36`

```bash
./gradlew assembleDebug
```

## 质量检查

```bash
./gradlew ktlintCheck detekt
./gradlew lint --max-workers=1
./gradlew :core:common:test :core:model:test :core:domain:test :core:network:testDebugUnitTest :core:data:testDebugUnitTest :core:media:testDebugUnitTest :core:ui:testDebugUnitTest :feature:home:testDebugUnitTest :feature:library:testDebugUnitTest :feature:audiobook:testDebugUnitTest :feature:player:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest --max-workers=1
./gradlew :core:database:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --max-workers=1
./gradlew :core:designsystem:assembleDebug :core:ui:assembleDebug :feature:home:assembleDebug :feature:library:assembleDebug :feature:audiobook:assembleDebug :feature:player:assembleDebug :feature:settings:assembleDebug assembleDebug assembleRelease --max-workers=1
```

连接模拟器或设备后运行：

```bash
./gradlew :core:database:connectedDebugAndroidTest :app:connectedDebugAndroidTest --max-workers=1
```

性能旅程使用接近 Release 的 `benchmark` 变体：

```bash
./gradlew :app:generateBaselineProfile --max-workers=1
./gradlew :benchmark:connectedBenchmarkAndroidTest --max-workers=1
```

模拟器仅验证旅程和趋势，发布性能结论必须来自稳定的物理设备。

Lint 与 Hilt 生成源码在并行构建时可能产生工具链文件竞争，因此 Lint、Release 构建和仪器测试在 CI 中按独立阶段执行。

## 发布

无签名的 Release 构建可直接用于本地验证。签名发布需要根目录的 `keystore.properties`；标签 `v*` 会触发 GitHub Release 工作流，生成签名 APK、SHA-256 校验和与自动变更说明。

## 文档

- [架构说明](docs/ARCHITECTURE.md)
- [质量标准](docs/QUALITY.md)
- [发布流程](docs/RELEASE.md)
- [路线图](docs/ROADMAP.md)
- [变更记录](CHANGELOG.md)
