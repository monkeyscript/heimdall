# Heimdall 👁️🛡️

> **The All-Seeing Local SMS Gatekeeper & Spam Shield for Android**

Heimdall is a private, lightweight, and local SMS inspection companion built for Android using Kotlin and Jetpack Compose. It detects fraudulent and promotional text messages using customizable keyword filters while protecting clean communications.

---

## ⚡ Features

* **100% Offline & Private**: Zero internet permission (`android.permission.INTERNET` is not included). Your SMS data never leaves your hardware.
* **Smart Keyword Engine**: User-managed customizable keyword rules (e.g. `loan`, `crypto`, `kyc`, `lottery`, `bonus`).
* **Smart OTP Extraction**: Intelligently detects verification codes and enables **1-tap OTP copy** directly from notification banners and message details.
* **Companion Notification System**: Pushes companion alert banners (`⚠️ Sender` for spam, `🛡️ Sender` for verified clean messages) with zero disruption to default SMS routing.
* **Futuristic Cyber-Minimalist UI**: Deep obsidian dark mode (`#121214`) with sharp geometric styling and electric amber accents adhering to an 8px layout grid.
* **Message Details Modal**: Full scrollable inspection modal with dynamic 1-tap OTP copying and instant deletion.

---

## 🛠️ Tech Stack & Architecture

* **Language**: Kotlin 2.0
* **UI Toolkit**: Jetpack Compose (Material 3)
* **SDK Compatibility**: Min SDK 26 (Android 8.0 Oreo) | Target SDK 34 (Android 14/15)
* **Storage**: Local Android `SharedPreferences` (JSON encrypted sandbox)
* **Telephony Stack**: Native Android `Telephony.Sms.Intents` Broadcast Receiver & `NotificationCompat`

---

## 🚀 Building & Running Locally

### Prerequisites:
* JDK 17 (Eclipse Temurin or Oracle JDK 17)
* Android SDK (API 34 + Build Tools)

### Build Debug APK:
```bash
./gradlew assembleDebug
```

### Install Directly via ADB:
```bash
./gradlew installDebug
```

---

## 🔒 Permissions Used
1. `RECEIVE_SMS`: Intercepts incoming cellular SMS broadcasts in real time.
2. `READ_SMS`: Parses multi-part SMS payloads for keyword analysis.
3. `POST_NOTIFICATIONS`: Renders Heimdall alert banners on Android 13+.

*Zero network, contacts, storage, camera, or location permissions.*

---

## 📄 License
MIT License
