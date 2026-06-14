# Meerkat Reader

<img src="./site/meerkat.png" width="120px">

Meerkat Reader is an Android RSS reader evolved from
[Capy Reader](https://github.com/jocmp/capyreader). It keeps the friendly
animal-themed spirit while continuing in a new direction with a different name,
visual identity, and feature set.

## Highlights

- Sync with Feedbin, FreshRSS, Miniflux, Google Reader API-compatible services,
  or local on-device feeds.
- Fast article navigation with unread, starred, saved-for-later, today, folder,
  feed, and saved-search views.
- Full-content extraction for reading complete articles inside the app.
- Article image caching and adjacent preloading for smoother reader navigation.
- Backup and restore for subscriptions, account settings, and app preferences.
- Customizable reader typography, themes, gestures, image visibility, and list
  density.
- Home screen widgets, feed update notifications, and media/audio playback
  support.
- OPML import/export and starred bookmark export.

## Thanks

Meerkat Reader is based on the excellent Capy Reader project by
[jocmp](https://github.com/jocmp) and its contributors. This fork is intended to
continue evolving independently so users can distinguish it from the original
project.

## Building the app

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
