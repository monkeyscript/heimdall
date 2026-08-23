# heimdall 👁️🛡️

> Lightweight, 100% offline SMS spam shield and smart OTP companion for Android.

---

## ✨ Features

* **🔒 100% Offline & Private**: Zero internet permissions (`android.permission.INTERNET` is not requested). Your SMS messages and data never leave your device.
* **🔍 Keyword Spam Engine**: Real-time inspection matching against customizable keywords (`loan`, `crypto`, `winner`, `kyc`, etc.).
* **⚡ Smart OTP 1-Tap Copy**: Intelligently extracts verification codes and renders a direct **`[ Copy 123456 ]`** action button on notification banners and message details.
* **📬 Companion Alert Feed**: Non-intrusive companion notifications (`⚠️ Sender` / `🛡️ Sender`) and a clean messenger-style inbox without altering your default messaging app.
* **🖤 Futuristic Cyber UI**: Pure dark obsidian theme (`#121214`) with electric amber accents, strict 8px layout grid, and instant stealth startup.
* **🗑️ Safe Deletion**: Purge all spam in 1 tap with confirmation guards for clean messages.

---

## 🔮 What's Coming (Roadmap)

* [ ] **RCS Notification Listener**: Detect and auto-dismiss rich marketing/promo ads from Google Messages (Jio/RCS).
* [ ] **Default SMS App Mode**: Total spam suppression—permanently dropping spam texts before they ring or save.
* [ ] **Contact Whitelist**: Auto-verify known contacts from your address book.
* [ ] **On-Device Smart ML Classifier**: Local tiny ML model for contextual spam detection beyond exact keywords.

---

## 🔒 Permissions Used

| Permission | Purpose |
| :--- | :--- |
| `RECEIVE_SMS` | Wakes Heimdall when an incoming cellular text arrives |
| `READ_SMS` | Inspects message text for keywords and OTP codes |
| `POST_NOTIFICATIONS` | Displays Heimdall companion alert banners (Android 13+) |

*Zero network, contacts, storage, camera, or location access.*

---

## 🛠️ Build & Install

```bash
# Build Debug APK
./gradlew assembleDebug

# Install directly to connected phone
./gradlew installDebug
```
