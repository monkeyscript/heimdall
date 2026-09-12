# Heimdall 👁️🛡️

[![Download APK](https://img.shields.io/github/v/release/monkeyscript/heimdall?label=Download%20APK&logo=android&color=success)](https://github.com/monkeyscript/heimdall/releases/latest)
[![No Internet](https://img.shields.io/badge/Permissions-Zero%20Internet-brightgreen.svg)]()

An Android SMS app built to silently block spam and keep it out of your system message storage.

## 🎭 Behind the curtain

Most spam blockers on Android just move spam into a "Spam" folder. The messages still get saved into your phone's central SMS database, and they still show up if you switch messaging apps or restore backups.

Heimdall works differently:
1. When set as your default SMS app, it intercepts incoming messages before they hit Android's system database.
2. If a message matches your spam rules, it gets dropped silently—no notification, no vibration, and it is **never written to the system SMS database**.
3. Real messages (bank alerts, OTPs, personal texts) are written to Android's SMS database normally.
4. If you ever switch back to Google Messages, your inbox stays clean because the spam was never stored on your phone in the first place.

> 💬 **Note on sending SMS**: Heimdall is designed for reading and filtering incoming messages, not composing new ones. If you need to send an SMS or use RCS, switch your default app back to Google Messages (there is a shortcut button right in Settings). All your clean messages will be right there.

## 🎁 Bonus points

- 🔇 **Silent spam blocking**: Blocked messages make no sound, show no alerts, and never touch the system database.
- 🗑️ **Auto-deletes old spam**: Blocked messages kept in Heimdall are automatically deleted after 30 days.
- 🎯 **Custom keyword rules**: Add any words or phrases to your blocklist (works with any language or script).
- 🔑 **1-tap OTP copy**: Extracts verification codes and puts a "Copy" button directly on the notification. Clicking it copies the code and dismisses the alert.
- 🔒 **100% offline**: No internet permission requested. None of your messages or data can leave your device.
- 🏷️ **Smart message categories**: Automatically tags incoming messages as Bank, Card, Delivery, Travel, or OTP on arrival.

## ⚖️ How it stacks up

| Feature | Heimdall | Standard FOSS SMS (Fossify / QKSMS) | Notification Filters (e.g., Buzzkill) | Proprietary Apps (Google Messages / Truecaller) |
| :--- | :---: | :---: | :---: | :---: |
| **Silent Pre-Storage Drop** *(Spam never reaches system SMS DB)* | ✅ | ❌ *(Stored in DB)* | ❌ *(Dismisses alerts only)* | ❌ *(Stored in DB)* |
| **100% Offline (No Internet Permission)** | ✅ | ✅ | ✅ | ❌ *(Requires network/cloud)* |
| **Auto 30-Day Spam Purge** | ✅ | ❌ *(Manual cleanup)* | ❌ | ❌ |
| **1-Tap OTP Copy & Auto-Dismiss** | ✅ | ❌ | ⚠️ *(Requires custom rules)* | ⚠️ *(Varies by OEM)* |
| **Offline Message Categorization** *(Bank, Card, Delivery, Travel, OTP)* | ✅ *(100% On-Device)* | ❌ | ❌ | ⚠️ *(Cloud-based / OEM specific)* |
| **No Account / No Telemetry** | ✅ | ✅ | ✅ | ❌ |

## 🚀 Get it running

### 📦 Download the APK
- **Direct Download**: Grab the latest signed APK from [GitHub Releases](https://github.com/monkeyscript/heimdall/releases/latest).
- **Auto-Updates with Obtainium**: Add `https://github.com/monkeyscript/heimdall` to [Obtainium](https://github.com/ImranR98/Obtainium) to get automated update alerts whenever a new version is released.

#### 💡 First-time installation guide (Android 13/14)
Since Heimdall is distributed directly outside the Google Play Store, Android will show a few standard security checks on first setup:

1. **Install from Files / Unknown Apps**:
   * When Android prompts: *"For your security, your phone currently isn't allowed to install unknown apps from this source"*:
   * **Steps**: Tap **Settings** → toggle on **Allow from this source** → press **Back** → tap **Install**.

2. **Google Play Protect Warning**:
   * When Android prompts: *"Blocked by Play Protect"* or *"Unsafe app blocked"*:
   * **Steps**: Tap **More details** (dropdown arrow) → tap **Install anyway** *(Heimdall has zero internet permissions, so your data never leaves the device)*.
   * *Alternative (to pause scanning)*: Open **Google Play Store** → tap **Profile icon** (top-right) → tap **Play Protect** → tap **⚙️ Settings** (top-right) → turn off **Scan apps with Play Protect**.

3. **Restricted Settings (if permissions are locked)**:
   * If Android prompts: *"Restricted setting: For your security, this setting is currently unavailable"*:
   * **Steps**: Open phone **Settings** → **Apps** → **All apps** → **Heimdall** (or long-press the Heimdall icon → **App info**) → tap the **three dots (`⋮`)** in the top-right corner → tap **Allow restricted settings** → verify with your fingerprint or PIN.

4. **Set as Default SMS App**:
   * When you open Heimdall, it automatically prompts: *"Set Heimdall as your default SMS app?"*.
   * **Steps**: Select **Heimdall** → tap **Set as default**. This automatically grants SMS access at the OS level, activates silent spam filtering, and loads your existing inbox.

### 💻 Build from source
Requires JDK 17+ and the Android SDK.

```bash
git clone https://github.com/monkeyscript/heimdall.git
cd heimdall

# Build signed release APK
./gradlew assembleRelease

# Or install directly to a connected phone
./gradlew installDebug
```
