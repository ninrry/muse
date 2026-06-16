# Muse

Muse is an Android local audio player focused on music, audiobooks, lyrics, and durable metadata editing.

It is built for users who keep audio files locally and expect edits to survive app reinstall, media rescans, and playback in other players. Muse writes supported metadata back into the source audio files instead of only storing it in an app-local database.

[Latest Release](https://github.com/ninrry/muse/releases/latest) |
[v2.0 APK](https://github.com/ninrry/muse/releases/download/v2.0/app-release.apk) |
[Changelog](CHANGELOG.md) |
[Roadmap](docs/ROADMAP.md)

![Latest Release](https://img.shields.io/github/v/release/ninrry/muse?label=release)
![Min SDK](https://img.shields.io/badge/minSdk-28-brightgreen)
![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-blue)
![License](https://img.shields.io/badge/license-MIT-green)

## What It Does

Muse keeps local listening practical:

- Scans local music and audiobook files.
- Plays through Android Media3 with notification and system media controls.
- Separates normal music from audiobook-style audio.
- Saves audiobook playback progress.
- Searches, displays, and offsets LRC lyrics.
- Fetches online metadata and cover candidates from multiple sources.
- Edits title, artist, album, year, genre, and cover.
- Writes metadata physically into supported source audio files.
- Keeps app display cache in sync with embedded file metadata.

## v2.0 Highlights

Version 2.0 focuses on metadata durability and search quality.

- Physical metadata writing for songs and audiobooks.
- Embedded cover writing for MP3, FLAC, OGG/Opus, M4A, M4B, MP4/ALAC, and WAV paths where the container supports it.
- Safer MP4/M4A/M4B atom rewriting for text tags and cover art.
- OGG/Opus Vorbis comment support for audiobook metadata.
- Cover image normalization before embedding to avoid large-image stalls.
- Search improvements across MusicBrainz/Cover Art Archive, NetEase, iTunes, Deezer, and QQMusic-style sources.
- Better ranking for original-song metadata matches.
- Media-session metadata refresh after file changes.

## Supported Audio Metadata

| Format | Text Tags | Embedded Cover | Notes |
| --- | --- | --- | --- |
| MP3 | Yes | Yes | ID3 through jaudiotagger |
| FLAC | Yes | Yes | Vorbis/FLAC tags |
| OGG / Opus | Yes | Yes | Custom OpusTags handling |
| M4A / M4B / MP4 / ALAC | Yes | Yes | Custom MP4 atom writer |
| WAV | Yes | Best effort | Depends on file/tag structure |

Unsupported or malformed files return explicit failures instead of silently falling back to soft metadata.

## Download

The current published APK is available on GitHub Releases:

- [Muse v2.0 release page](https://github.com/ninrry/muse/releases/tag/v2.0)
- [Direct APK download](https://github.com/ninrry/muse/releases/download/v2.0/app-release.apk)

Android requirement: Android 9.0 or newer (`minSdk 28`).

## Permissions

Muse needs storage access to scan and modify local audio files. On modern Android versions, physical file writes may require broad file access or an elevated file writer path such as Shizuku, depending on where the audio files are stored.

If Muse cannot write a file directly, it reports the permission problem instead of pretending the metadata was saved.

## Project Structure

```text
app/                         Android app entry, navigation, DI, app-level use cases
core/common/                 Shared utilities and result types
core/model/                  Domain models
core/domain/                 Repository contracts and use cases
core/data/                   Scanner, repositories, metadata writers, tag parsers
core/database/               Room database, DAO, migrations
core/network/                Metadata, cover, and lyrics network clients
core/media/                  Media3 player and service integration
core/designsystem/           Theme, typography, colors, reusable design tokens
core/ui/                     Shared Compose UI components
feature/home/                Home screen
feature/library/             Music library and metadata editing
feature/audiobook/           Audiobook library and collections
feature/player/              Player screen and lyrics panel
feature/settings/            Settings
benchmark/                   Macrobenchmark target
baselineprofile/             Baseline profile generation
build-logic/                 Convention plugins
```

The dependency direction follows a Clean Architecture style: features depend on domain contracts, data implements those contracts, and app wires the graph together through Hilt.

## Build Locally

Requirements:

- JDK 17
- Android Studio or Android SDK command-line tools
- Android SDK 36

Clone and build:

```bash
git clone https://github.com/ninrry/muse.git
cd muse
./gradlew assembleDebug
```

Install a debug build:

```bash
./gradlew installDebug
```

Build a signed release APK:

```bash
./gradlew assembleRelease
```

Release signing is read from `keystore.properties` in the repository root. The file is intentionally ignored by git.

```properties
keystorePath=../your.keystore
keystorePwd=your_keystore_password
keyAliasName=your_key_alias
keyPwd=your_key_password
```

The release APK is generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Checks

Useful local checks:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :core:data:testDebugUnitTest
./gradlew testDebugUnitTest
./gradlew lint
```

For release validation:

```bash
./gradlew :app:assembleRelease --stacktrace
```

## Release Process

The Android version is defined in [app/build.gradle.kts](app/build.gradle.kts):

```kotlin
versionCode = 20
versionName = "2.0"
```

Create and publish a GitHub release after building the signed APK:

```bash
gh release create v2.0 \
  app/build/outputs/apk/release/app-release.apk#Muse-v2.0.apk \
  --title "Release v2.0" \
  --latest
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Quality](docs/QUALITY.md)
- [Release](docs/RELEASE.md)
- [Roadmap](docs/ROADMAP.md)
- [Changelog](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md)
- [Security](SECURITY.md)

## License

Muse is released under the [MIT License](LICENSE).
