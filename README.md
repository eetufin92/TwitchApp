# TwitchApp

A modern, feature-rich Twitch client for Android built with **Jetpack Compose** and Material 3. Inspired by the architecture of `YoutubeApp`, this client solves major pain points of the official Twitch mobile app by adding full **third-party emote support (7TV, BetterTTV, FrankerFaceZ)**, **automatic SSAI ad muting with a clean overlay**, a **floating resizable chat**, and a **multistream multi-view mode** for viewing up to 4 streams concurrently on phones and tablets.

---

## Key Features

### 1. Full Third-Party Emote Support (7TV, BTTV, FFZ)
- **No More Confusing Text**: Automatically resolves and renders popular streamer emotes like `KEKW`, `Pog`, `catJAM`, `monkaW`, `OMEGALUL`, `Pepega`, `widepeppoHappy`, `COPIUM`, `Sadge`, `AYAYA`, `EZ`, `Clap`, etc.
- **Offline Instant Bundle**: Ships with an embedded bundle of top global emotes so common emotes render immediately without waiting for network responses.
- **Live Channel Fetching**: Automatically detects the active stream's channel, resolves the broadcaster ID, and loads channel-specific emote sets from 7TV, BTTV, and FFZ.
- **High-Performance In-Chat Replacer**: Seamlessly transforms chat text nodes into animated WebP/GIF/PNG images without lagging high-speed chats.

### 2. Automatic SSAI Ad Muting & "Ad in Progress" Overlay
- **Protects Your Ears**: Twitch serves ads server-side (SSAI) directly stitched into the stream. When an ad break occurs, TwitchApp immediately mutes the audio.
- **Clean Visual Shield**: Replaces loud/jarring ad content with a sleek, calming dark overlay featuring countdown timer information extracted from the ad metadata.
- **Manual Unmute Override**: Easily unmute at any time if you wish to listen to the ad.
- **Auto-Restoration**: As soon as the live broadcast resumes, stream audio and video are smoothly restored.

### 3. Floating, Resizable & Draggable Chat
- **Move Anywhere**: Drag the floating chat window from the header anywhere on screen with automatic boundary clamping.
- **Fluid Corner Resizing**: Drag the bottom-right resize handle to make chat as wide or tall as you prefer.
- **Adjustable Opacity**: Make chat semi-transparent (100%, 80%, 60%, 40%) so you can read chat overlaid directly on top of the video stream.
- **Minimize to Floating Bubble**: Collapse chat into a small circular avatar pill when you want clean gameplay, and tap to instantly restore.

### 4. Multistreaming (Multi-View)
- **Watch Up to 4 Streams**: Dynamic grid supporting 1, 2, 3, or 4 simultaneous live streams.
- **Smart Audio Selector**: Prevents audio cacophony by maintaining only one active unmuted stream at a time. Tap any stream tile or speaker icon to switch audio focus immediately.
- **Quick-Add Channel**: Search or pick from top streamers to quickly populate your multi-view grid.
- **Integrated Chat Switcher**: Switch the floating chat to any of your open streams with a single tap.

### 5. Adaptive Layout for Phones & Tablets
- **Responsive Sizing**:
  - **Phones (<600dp)**: Compact mobile web player with clean gesture controls, chat integration, and automatic Picture-in-Picture (PiP).
  - **Tablets & Foldables (>=600dp)**: Automatically loads full desktop Twitch layout with side-by-side stream and live chat, theater mode, and high-resolution video.
- **User Agent Customization**: Toggle between Mobile Web and Desktop Web layouts on demand.

### 6. Picture-in-Picture (PiP) & Background Audio
- **Seamless PiP**: Automatically enters Picture-in-Picture when pressing Home or switching apps during video playback, with accurate aspect ratio.
- **Background Audio**: Integrated with `MediaSession` and Android's foreground service to continue listening to streams when the screen is turned off.

### 7. uBlock Origin AdBlocker Integration
- **Official uBlock Origin Filter Lists**: Directly subscribes to and synchronizes with official community filter lists (*uBlock filters*, *uBlock Badware*, *uBlock Privacy*, *uBlock Quick fixes*, *EasyList*, *EasyPrivacy*, *Peter Lowe's*).
- **Network Interception & Cosmetic Hiding**: Intercepts third-party telemetry, tracking, and ad requests returning HTTP 204 No Content, and injects CSS element-hiding rules.
- **Twitch Protections**: Specifically safeguards Twitch live stream manifests, GQL endpoints, and emote CDNs to guarantee zero playback interruption.

---

## Tech Stack & Architecture

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 & M3 Adaptive.
- **Navigation**: Modern [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) (`androidx.navigation3`).
- **WebView Engine**: Hardware-accelerated `PersistentWebView` with `TwitchAndroidBridge` JavaScript interface and custom script injections (`twitch_injections.js`).
- **Networking**: [OkHttp](https://square.github.io/okhttp/) & [Retrofit](https://square.github.io/retrofit/) for 7TV, BTTV, and FFZ REST APIs.
- **JSON Parsing**: [Moshi](https://github.com/square/moshi) with Kotlin codegen.
- **Image Loading**: [Coil Compose](https://coil-kt.github.io/coil/compose/) for animated WebP/GIF previewing.
- **Preferences**: SharedPreferences / DataStore for persistent settings.

---

## Project Structure

```
TwitchApp/
├── .github/
│   └── workflows/
│       └── android.yml          # GitHub Actions CI/CD (assembleRelease, APK signing, GitHub Release)
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── twitch_injections.js     # Emote replacement, SSAI ad muting, cosmetic cleanup
│       ├── java/com/eetu/twitchapp/
│       │   ├── MainActivity.kt          # App entry point, Navigation 3, PiP
│       │   ├── bridge/
│       │   │   └── TwitchAndroidBridge.kt
│       │   ├── data/
│       │   │   ├── AdBlockManager.kt    # uBlock Origin list downloader & interceptor
│       │   │   ├── EmoteRepository.kt    # 7TV, BTTV, FFZ API fetcher & cache
│       │   │   ├── TwitchSettingsManager.kt
│       │   │   └── model/
│       │   │       └── EmoteModels.kt
│       │   ├── navigation/
│       │   │   └── Destinations.kt      # Player, MultiStream, Settings
│       │   ├── service/
│       │   │   └── PlaybackService.kt   # Background audio foreground service
│       │   └── ui/
│       │       ├── components/
│       │       │   ├── TwitchWebView.kt # Web player, SSAI overlay, draggable FAB menu, top bar
│       │       │   └── FloatingResizableChat.kt
│       │       ├── multistream/
│       │       │   └── MultiStreamScreen.kt # Multi-view 1-4 stream grid
│       │       ├── settings/
│       │       │   ├── SettingsScreen.kt
│       │       │   ├── AdBlockSettingsScreen.kt
│       │       │   ├── EmoteSettingsScreen.kt
│       │       │   ├── AdSettingsScreen.kt
│       │       │   └── ChatAppearanceSettingsScreen.kt
│       │       └── theme/
│       │           ├── Color.kt
│       │           ├── Theme.kt
│       │           └── Type.kt
│       └── res/
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## Getting Started

1. Open the project in Android Studio (Jellyfish or newer).
2. Sync project with Gradle files.
3. Run on any Android device or emulator (API 24+).
