# Personal Browser (WebWrap) 🚀

A privacy-focused, lightweight Android browser built with **Jetpack Compose** and **WebView**. This browser is designed for users who want persistent sessions, background audio capabilities (ideal for video-to-audio transitions), and a clean, modern UI.

## ✨ Key Features

- **Background Audio Playback:** Keep listening to audio from websites even when the app is in the background or the screen is off.
- **Persistent Sessions:** Automatically saves your cookies and open tabs, so you never lose your place.
- **Display Cutout Support:** Optimized for modern edge-to-edge displays, utilizing the notch/cutout area for an immersive experience.
- **Smart Landscape Mode:** Automatic fullscreen and system bar hiding for video playback.
- **Custom Bookmarks & History:** Easily manage your favorite sites with a visual dashboard.
- **Automated Maintenance:** Clears expired cookies and performs startup cleanup to keep the app fast.
- **Material 3 UI:** A modern "Catppuccin-inspired" dark theme for comfortable night browsing.

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Design:** Material 3
- **Storage:** SharedPrefs & Internal Storage (via Gson) for Bookmarks/History
- **Networking:** Android WebView with custom CookieManager integration
- **Media:** Android Media APIs for background audio support

## 📸 Screenshots

| Home Screen | Browser View |
| :---: | :---: |
| *[Add Home Screenshot Here]* | *[Add Browser Screenshot Here]* |

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug or newer
- Android SDK 28 (Pie) or higher
- Kotlin 2.0+

### Installation
1. Clone the repository:
2. Open the project in **Android Studio**.
3. Sync Gradle and run the app on your physical device or emulator.

## 📂 Project Structure

- `MainActivity.kt`: The entry point, handling system UI (cutouts, status bars) and navigation.
- `webview/`: Custom WebView implementation and tab management.
- `data/`: Storage logic for bookmarks, history, and session persistence.
- `service/`: Background services for audio playback.

## 🚧 Roadmap & Future Improvements
- [ ] Ad-blocker integration (Domain filtering)
- [ ] Biometric lock (Fingerprint/Face ID) for private bookmarks
- [ ] Pull-to-refresh functionality
- [ ] Desktop Mode toggle
- [ ] Multi-tab switcher UI

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
*Developed with ❤️ as a personal browsing solution.*
   