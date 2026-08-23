# heimdall 👁️🛡️

A lightweight, 100% offline SMS spam shield and smart OTP companion for Android.

---

## Features

* **100% Offline & Private**: Zero internet permissions. All message processing and storage stays strictly on your device.
* **Keyword Spam Filter**: Real-time SMS inspection matching against customizable keyword rules (`loan`, `crypto`, `kyc`, etc.).
* **Smart OTP 1-Tap Copy**: Auto-extracts verification codes and adds a direct `[ Copy 123456 ]` button to notification banners and message details.
* **Companion Mode**: Delivers companion alerts (`⚠️ Spam` / `🛡️ Clean`) alongside your existing SMS app without disrupting normal message delivery.

---

## Permissions

* `RECEIVE_SMS`: Intercepts incoming cellular SMS broadcasts in real time.
* `READ_SMS`: Reads message text for keyword inspection and OTP extraction.
* `POST_NOTIFICATIONS`: Displays companion alerts on Android 13+.

*No internet, contacts, or location permissions.*

---

## Build

```bash
# Build Debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug
```

---

## What's Next

* [ ] **RCS Notification Interceptor**: Silence promotional RCS chat ads from Google Messages.
* [ ] **Default SMS App Mode**: Full spam suppression with automatic message deletion.
* [ ] **Contact Whitelist**: Auto-verify messages from saved contacts.
* [ ] **On-Device ML Classifier**: Local machine learning model for contextual spam detection.
