# Muse

Android 本地音频播放器。支持音乐播放、歌词、元数据编辑和 ReadAlong 同步有声书阅读。

## 功能

- 扫描本地音乐和有声书
- Media3 ExoPlayer 播放内核，通知栏和系统媒体控制
- 自动区分音乐与有声书
- ReadAlong 同步阅读：导入 `.readalong.zip`，逐字高亮与音频同步
- 有声书章节级断点续听
- 歌词搜索、显示和手动校正（网易云 / QQ / 酷狗 / 酷我）
- 逐字卡拉 OK 填色动画
- 悬浮歌词（拖动吸附、锁定穿透）
- 歌曲元数据和封面搜索与编辑
- 元数据物理写入音频源文件

## 音频格式

| 格式 | 标签 | 封面 |
| --- | --- | --- |
| MP3 | Y | Y |
| FLAC | Y | Y |
| OGG/Opus | Y | Y |
| M4A/M4B/MP4 | Y | Y |
| WAV | Y | - |

## 构建

JDK 17, Android SDK 36.

```
./gradlew assembleDebug
```

Release 构建需在项目根目录放置 `keystore.properties`：

```
keystorePath=/path/to/keystore.jks
keystorePwd=<password>
keyAliasName=<alias>
keyPwd=<password>
```

## 技术栈

Kotlin 2.3 / Compose (BOM 2026.05) / Media3 1.10 / Room 2.8 / Hilt 2.58 / KSP 2.3.9 / Gradle 9.4

## 许可

MIT License — 见 [LICENSE](LICENSE)
