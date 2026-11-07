# HeliBoard Telex

<p align="center">
  <strong>Privacy-conscious, 100% offline Android keyboard with an intelligent native Vietnamese Telex engine.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPL v3">
  <img src="https://img.shields.io/badge/Internet_Permission-NONE_(100%25_Offline)-brightgreen.svg" alt="No Internet">
  <img src="https://img.shields.io/badge/Language-Vietnamese%20%2B%20Multilingual-orange.svg" alt="Vietnamese + Multilingual">
  <img src="https://img.shields.io/badge/Architecture-arm64--v8a-informational.svg" alt="arm64-v8a">
</p>

---

## 🌟 Overview

**HeliBoard Telex** is an enhanced fork of [HeliBoard](https://github.com/HeliBorg/HeliBoard) (based on OpenBoard / AOSP Keyboard) featuring a **built-in intelligent Vietnamese Telex engine** and a **bundled Vietnamese dictionary**.

Unlike standard AOSP keyboards that rely on basic combiners or require manual dictionary downloads, HeliBoard Telex offers a modern, desktop-class Vietnamese typing experience (inspired by UniKey, OpenKey, and EVKey) directly on Android while keeping the zero-internet privacy guarantee.

---

## ✨ Vietnamese Telex Engine Features

HeliBoard Telex integrates a high-performance, context-aware Telex engine (`com.viettelex`) and comes pre-loaded with Vietnamese lexicon data:

* **🎯 Free Diacritic Placement (*Bỏ dấu tự do*)**: Type tone and accent keys at any point within a syllable (e.g. typing `thuongw` or `thuowng` both yield `thương`).
* **🔤 Contextual English Detection (*Nhận diện tiếng Anh theo ngữ cảnh*)**: Intelligently identifies English words and phrases to minimize accidental tone placement, with automatic keystroke restoration on word boundaries.
* **⚡ Simple Telex (*Telex đơn giản*)**: Keeps `w` at the beginning of a word as `w` (e.g., `web`, `wifi`) instead of auto-converting to `ư`.
* **👆 Adjacent-Key Correction (*Gợi ý sửa lỗi chạm trượt*)**: Detects mistyped neighboring keys on touch screens and surfaces corrections directly on the suggestion strip.
* **🛡️ Live Spell Checking (*Kiểm tra chính tả khi gõ*)**: Syllable validation prevents illegal Vietnamese phoneme combinations while typing.
* **✏️ Re-Edit Completed Words (*Sửa dấu từ đã gõ*)**: Backspace or tap back into previously typed words to adjust or add diacritics without having to retype the whole word.
* **📚 Bundled Vietnamese Dictionary**: Includes `main_vi.dict` and `vnlexicon.bin` out of the box—no external dictionary setup required for spellchecking, predictions, and autocorrection.
* **⚙️ Clean Per-Language Settings**: Vietnamese-specific preferences live exclusively within the Vietnamese layout configuration screen (`Settings -> Languages -> Vietnamese`), keeping global preference screens clean and uncluttered.

---

## 🔒 Privacy & Core HeliBoard Features

* **100% Offline**: Requires **NO internet permission** in AndroidManifest. Your keystrokes never leave your device.
* **Custom Themes**: Dynamic Material You colors (Android 12+), dark/light theme matching, and full custom color styling.
* **Clipboard History**: Built-in clipboard manager with pinned clips and auto-purge options.
* **Multiple Layouts**: QWERTY, QWERTZ, AZERTY, Dvorak, Colemak, PC layout, and customizable layouts.
* **One-Handed & Split Keyboard**: Ergonomic typing modes for large devices and foldables.
* **Glide Typing**: Compatible with gesture typing libraries extracted from standard GApps packages.
* **Backup & Restore**: Easily export and import your keyboard settings and learned words.

---

## 🚀 Getting Started

### Installation
Download the latest APK (`arm64-v8a`) from the **[Releases](../../releases)** tab or the latest **[Actions Artifacts](../../actions)**.

### Enabling the Keyboard
1. Go to **Settings → System → Languages & Input → On-screen keyboard**.
2. Enable **HeliBoard Telex**.
3. Select **HeliBoard Telex** as your default input method.
4. Open keyboard settings → **Languages** → enable **Vietnamese** to configure Telex preferences.

---

## 🛠️ Building from Source

### Prerequisites
- JDK 17+
- Android SDK (API 37, Build Tools, NDK)

### Build APK
```bash
# Clone the repository
git clone git@github.com:tecix/heliboard-telex.git
cd heliboard-telex

# Build debug APK (arm64-v8a)
./gradlew assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/HeliBoard-Telex_*-debug.apk`

---

## 📜 License & Credits

- **HeliBoard Telex** is licensed under [GNU General Public License v3.0](LICENSE).
- Portions derived from AOSP Keyboard are licensed under [Apache License 2.0](LICENSE-Apache-2.0).
- Icon licensed under [CC BY-SA 4.0](LICENSE-CC-BY-SA-4.0).

### Acknowledgements
- [HeliBoard](https://github.com/HeliBorg/HeliBoard) / [Helium314](https://github.com/Helium314)
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
