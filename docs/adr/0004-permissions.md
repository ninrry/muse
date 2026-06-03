# ADR-0004: 权限处理决策

## 状态

已接受

## 决策标题

采用运行时权限请求，优雅降级策略

## 上下文

Muse 需要访问用户设备上的音频文件，涉及存储权限：
- Android 12 及以下：`READ_EXTERNAL_STORAGE`
- Android 13+：`READ_MEDIA_AUDIO`

需要处理以下场景：
- 首次安装时的权限请求
- 用户拒绝权限后的降级体验
- 用户永久拒绝后的引导

## 决策

采用 **运行时权限请求** + **优雅降级** 策略：

### 权限策略

1. **首次启动**: 显示权限说明对话框，然后请求权限
2. **用户允许**: 正常使用所有功能
3. **用户拒绝**: 显示空状态，提示需要权限
4. **永久拒绝**: 引导用户前往设置页面

### 实现方式

```kotlin
// 权限检查
fun hasAudioPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

// 权限请求
@Composable
fun RequestAudioPermission(onResult: (Boolean) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onResult(isGranted)
    }
    // ...
}
```

### UI 处理

```kotlin
@Composable
fun HomeScreen(hasPermission: Boolean, onRequestPermission: () -> Unit) {
    if (hasPermission) {
        MusicContent()
    } else {
        PermissionRequiredContent(onRequestPermission)
    }
}
```

## 后果

### 正面影响

- **用户体验**: 清晰的权限说明，尊重用户选择
- **功能可用**: 授权后正常使用
- **合规性**: 符合 Android 权限最佳实践

### 负面影响

- **功能受限**: 未授权时无法使用核心功能
- **开发复杂度**: 需要处理多种权限状态

### 风险缓解

- 提供清晰的权限说明
- 优雅降级，不崩溃
- 引导用户前往设置

## 相关决策

- [ADR-0001: 架构决策](./0001-architecture.md)

## 参考资料

- [Android 权限指南](https://developer.android.com/training/permissions/requesting)
- [Android 13 权限变更](https://developer.android.com/about/versions/13/behavior-changes-13)
