# Muse

A premium local music player for Android — **安静、克制、温暖**的数字唱片馆。

> Muse 不是"能用的播放器"。它是**数字唱片馆**——每个像素、每次交互、每帧动画都在传递"安静、克制、温暖"的情绪。

## Features

- 🎵 **Broad format support**: MP3, AAC, FLAC, ALAC, WAV, OGG, Opus, APE, AIFF, MP4A (AAC/ALAC), AC-3, DSD
- 🏷️ **Rich metadata parsing**: Title, artist, album, codec, bitrate, sample rate, bit depth, duration
- 🎨 **Material Design 3**: Beige-based warm color scheme, light & dark themes
- 📂 **Smart scanning**: Full device scan + custom folder selection
- 📋 **Library management**: Browse by Songs / Albums / Artists
- ✏️ **File management**: Rename and delete songs directly from the app
- ▶️ **Playback**: Play/pause, skip, seek, repeat modes, shuffle
- 📜 **Lyrics**: Auto-fetch synced lyrics via LRCLIB + Netease Cloud Music cascading
- 🔄 **Traditional → Simplified Chinese**: Auto-conversion for all lyrics
- 🎴 **Default cover generation**: Auto-generated album art for songs without covers
- ⏰ **Sleep timer**: Timer-based or track-end playback stop
- 🔎 **Search**: Search by song title, artist, or album

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | **Kotlin** 2.1.0 |
| UI | **Jetpack Compose** + Material Design 3 |
| Player | **Media3** ExoPlayer 1.9.3 |
| Database | **Room** 2.6.1 |
| DI | Manual (no framework) |
| Navigation | **Navigation Compose** |
| Image Loading | **Coil** |
| Metadata Tagging | **jaudiotagger** |
| Build | **Gradle** with Kotlin DSL + Version Catalog |

## Screenshots

*(Coming soon)*

## Getting Started

### Prerequisites

- Android Studio Ladybug (2024.2+) or IntelliJ IDEA
- JDK 17+
- Android SDK 35

### Build

```bash
# Clone the repository
git clone https://github.com/ninrry/muse.git
cd muse

# Set up signing (optional, for release builds)
cp keystore.properties.example keystore.properties
# Edit keystore.properties with your own keystore path and credentials

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease
```

### Install

The debug APK can be installed directly:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or transfer the release APK to your device and install manually.

## Project Structure

```
muse/
├── app/
│   └── src/main/
│       ├── java/luzzr/muse/
│       │   ├── data/
│       │   │   ├── database/    # Room entities, DAOs
│       │   │   ├── model/       # Data models
│       │   │   ├── network/     # API clients, parsers
│       │   │   └── repository/  # MusicRepository
│       │   ├── player/          # MusicService, PlayerState
│       │   └── ui/
│       │       ├── animation/   # Animation specs
│       │       ├── components/  # Shared UI components
│       │       └── screens/     # Screens: player, library, settings, home
│       └── res/                 # Resources
├── docs/                        # Development docs
├── build.gradle.kts             # Root build script
├── soul.md                      # Project philosophy
└── PROJECT.md                   # Project metadata
```

## Lyrics Sources

Lyrics are fetched through a cascading fallback chain:

1. **LRCLIB** (exact match by track + artist + album)
2. **LRCLIB** (fuzzy search)
3. **Netease Cloud Music** (best coverage for Chinese songs, via `music.163.com/api`)

All lyrics are automatically converted from Traditional to Simplified Chinese.

## License

MIT License — see [LICENSE](LICENSE) for details.
