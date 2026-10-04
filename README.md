<p align="center">
  <img src="site/meerkat-reader-icon.png" width="144" height="144" alt="Meerkat Reader icon" />
</p>

<h1 align="center">Meerkat Reader</h1>

<p align="center">给纷繁的信息，留一个安静的阅读角落。</p>

<p align="center">简体中文 · <a href="README_EN.md">English</a></p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-3b82f6.svg" alt="GPL-3.0" /></a>
  <img src="https://img.shields.io/badge/platform-Android%2011%2B-3ddc84.svg" alt="Android 11 and later" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7f52ff.svg" alt="Kotlin and Jetpack Compose" />
</p>

Meerkat Reader 是一款基于 [Capy Reader](https://github.com/jocmp/capyreader) 演进的 Android RSS 阅读器。将订阅、全文阅读、稍后读和语音朗读放在一起，用适合自己的排版与手势，按自己的节奏追踪更新。

## 为什么选择 Meerkat Reader

既可以在设备上管理本地订阅，也可以接入已有的 RSS 服务。通过未读、收藏、今天、文件夹和保存的搜索整理文章，再用全文提取、图片缓存与阅读外观设置，让阅读更连贯。

## ✨ 功能亮点

- 📰 **订阅与同步**：支持 Feedbin、FreshRSS、Miniflux、Google Reader API 兼容服务，以及本地订阅。
- 🔎 **整理与查找**：按未读、收藏、稍后读、今天、文件夹和订阅源浏览，支持保存搜索。
- 📖 **全文阅读**：在应用内提取文章全文，支持文章图片缓存与相邻文章预加载。
- 🎧 **听文章**：支持 TTS 语音朗读，以及媒体和音频播放。
- 🎨 **阅读外观**：调整字体、主题、手势、图片显示和列表密度。
- 📥 **稍后阅读**：通过 Wallabag 集成保存文章。
- 💾 **备份与迁移**：备份和恢复订阅、账户设置与应用偏好，支持 WebDAV 备份、OPML 导入导出及收藏书签导出。
- 🏠 **日常入口**：提供桌面小组件与订阅更新通知。

## 下载与运行环境

支持 **Android 11（API 30）及以上版本**。安装包与版本说明见 [GitHub Releases](https://github.com/ron159/MeerkatReader/releases)；Nightly 为开发预览版本，下载时请留意对应版本说明。

## 本地开发

使用 Android Studio 和 JDK 21，克隆仓库后同步 Gradle，再运行 `app`。

构建调试 APK：

```sh
./gradlew :app:assembleFreeDebug
```

产物位于 `app/build/outputs/apk/free/debug/app-free-debug.apk`。

### 签名构建

默认使用调试密钥。自定义发布签名时，在仓库根目录放置 `release.keystore`，并创建 `secrets.properties`：

```properties
key_alias=
store_password=
key_password=
```

不要将密钥文件或签名凭据提交到版本控制。

## 上游项目与致谢

Meerkat Reader 使用 [GNU GPL v3](LICENSE) 开源，基于 [Capy Reader](https://github.com/jocmp/capyreader) 持续演进。感谢 [jocmp](https://github.com/jocmp) 与所有上游贡献者。

本项目保留了友好的动物主题，并以独立名称、视觉形象和功能方向继续开发，便于与原项目区分。
