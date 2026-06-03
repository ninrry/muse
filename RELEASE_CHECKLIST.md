# 发布检查清单 (Release Checklist)

每次准备打包新的 Release 版本前，请依次完成以下检查。

## 1. 代码质量与测试
- [ ] 运行 `./gradlew ktlintCheck` (或类似代码格式化工具，如有)
- [ ] 运行 `./gradlew testDebugUnitTest`，确保**所有业务逻辑单元测试通过**。
- [ ] 运行 `./gradlew connectedAndroidTest`，确保**Room Migration 测试通过**（避免升级毁库）。
- [ ] 运行 `./gradlew lint`，无严重 (Fatal) 警告。

## 2. UI 与适配检查
- [ ] 深色模式 (Dark Mode) 与浅色模式切换显示正常。
- [ ] 系统字体调至“特大号”，界面无严重遮挡或截断。
- [ ] 小屏设备 (如 4.7 寸) 与大屏设备 (折叠屏展开) 布局未崩坏。
- [ ] TalkBack 开启后，播放控制按钮 (`Play/Pause/Next`) 有明确的 `contentDescription` 播报。

## 3. 核心功能链路
- [ ] 授权读取本地媒体，首次扫描所有歌曲正常展示。
- [ ] 后台播放：按下 Home 键返回桌面，音乐继续播放，通知栏控制条 (MediaStyle) 工作正常。
- [ ] 歌词抓取：测试无网络和有网络状态下，在线歌词回落逻辑正常。
- [ ] 睡眠定时 (SleepTimer)：到达时间后能够自动暂停播放。

## 4. 版本更新与打包
- [ ] 修改 `app/build.gradle.kts` 中的 `versionCode` (递增)。
- [ ] 修改 `app/build.gradle.kts` 中的 `versionName` (符合 Semantic Versioning 语义)。
- [ ] 确保 `keystore.properties` 凭证配置无误。
- [ ] 运行 `./gradlew assembleRelease` 生成最终 APK，并验证 APK 体积是否在合理范围内。
