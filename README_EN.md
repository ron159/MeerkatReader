<p align="center">
  <img src="site/meerkat-reader-icon.png" width="144" height="144" alt="Meerkat Reader icon" />
</p>

<h1 align="center">Meerkat Reader</h1>

<p align="center">A quiet corner for a busy reading list.</p>

<p align="center"><a href="README.md">简体中文</a> · English</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-3b82f6.svg" alt="GPL-3.0" /></a>
  <img src="https://img.shields.io/badge/platform-Android%2011%2B-3ddc84.svg" alt="Android 11 and later" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7f52ff.svg" alt="Kotlin and Jetpack Compose" />
</p>

Meerkat Reader is an Android RSS reader evolved from [Capy Reader](https://github.com/jocmp/capyreader). Bring subscriptions, full articles, read-later tools, and text-to-speech together, with typography and gestures that fit the way you read.

## Why Meerkat Reader

Keep feeds on your device or connect an existing RSS service. Organize articles through unread, starred, today, folder, and saved-search views, then settle into reading with full-content extraction, image caching, and customizable reader settings.

## ✨ Highlights

- 📰 **Feeds and sync**: Feedbin, FreshRSS, Miniflux, Google Reader API-compatible services, and local feeds.
- 🔎 **Organization**: Unread, starred, saved-for-later, today, folder, feed, and saved-search views.
- 📖 **Full articles**: Full-content extraction, article image caching, and adjacent article preloading.
- 🎧 **Listen**: Text-to-speech for articles, plus media and audio playback.
- 🎨 **Your reading style**: Customizable typography, themes, gestures, image visibility, and list density.
- 📥 **Read later**: Save articles through the Wallabag integration.
- 💾 **Backup and migration**: Back up subscriptions, account settings, and preferences; use WebDAV backups, OPML import/export, and starred bookmark export.
- 🏠 **Everyday access**: Home screen widgets and feed update notifications.

## Downloads and requirements

Requires **Android 11 (API 30) or later**. See [GitHub Releases](https://github.com/ron159/MeerkatReader/releases) for packages and release notes. Nightly builds are development previews; check the notes for the build you download.

## Building the app

Requires JDK 21.

### Getting Started

1. Clone this repository.
2. Install [Android Studio](https://developer.android.com/studio) if you do not
   have it already.
3. Sync Gradle.
4. In the toolbar, go to Run > Run 'app'.

### Build a debug APK

From the project root:

```sh
./gradlew :app:assembleFreeDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/free/debug/app-free-debug.apk
```

### Build a signed release

By default the app will build with a debug keystore. To build a signed release,
place `release.keystore` in the root directory, then create `secrets.properties`
with:

```properties
key_alias=
store_password=
key_password=
```

Do not commit signing keys or credentials to version control.

## Upstream and acknowledgments

Meerkat Reader is licensed under [GNU GPL v3](LICENSE) and builds on [Capy Reader](https://github.com/jocmp/capyreader). Thank you to [jocmp](https://github.com/jocmp) and all upstream contributors.

This fork keeps the friendly animal-themed spirit while evolving with its own name, visual identity, and feature set so users can distinguish it from the original project.
