# 发布流程

## 发布前检查

1. 运行 `./gradlew ktlintCheck detekt lint :core:common:test :core:model:test :core:domain:test :core:network:testDebugUnitTest :core:data:testDebugUnitTest :core:media:testDebugUnitTest :core:ui:testDebugUnitTest :feature:home:testDebugUnitTest :feature:library:testDebugUnitTest :feature:audiobook:testDebugUnitTest :feature:player:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest :core:database:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :feature:library:assembleDebug :feature:player:assembleDebug assembleRelease --max-workers=1`。
2. 在 API 28 与 API 35 设备验证播放、通知控制、队列、重复/随机、睡眠定时、歌词、扫描、有声书合集和手动 / 自动元数据写入失败恢复。
3. 运行 `./gradlew :core:database:connectedDebugAndroidTest --max-workers=1`，确认所有 Room 迁移路径无需清库。
4. 在固定物理设备运行 `./gradlew :benchmark:connectedBenchmarkAndroidTest --max-workers=1`，检查 JSON 与 Perfetto traces，确认无冻结帧和超过 10% 的基准回退。
5. 更新 `CHANGELOG.md` 与版本号。

## 本地签名

根目录创建已被 Git 忽略的 `keystore.properties`：

```properties
keystorePath=path/to/release.keystore
keystorePwd=...
keyAliasName=...
keyPwd=...
```

## GitHub Release

推送 `v*` 标签会触发 Release 工作流。仓库需配置：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

工作流会执行完整质量检查，生成签名 APK、SHA-256 校验和与自动变更说明。APK 不得提交到 Git。
