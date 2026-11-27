# Installation & Build Guide

This guide covers standalone APK installation, automated updates via Obtainium, building Messagate from source, and troubleshooting Android sideloading permissions.

---

## Installation Methods

### 1. Direct APK Download (GitHub Releases)

Download the latest pre-compiled, signed APK directly:
- [Download Latest Release](https://github.com/karjok/heimdall/releases/latest)

---

### 2. Automated Updates with Obtainium

If you prefer installing directly from GitHub releases with automated update notifications:
1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Tap **Add App**.
3. Enter the repository URL:
   ```
   https://github.com/karjok/heimdall
   ```
4. Save and install.

---

### 3. Build from Source

Requirements:
- JDK 17+
- Android SDK (API 34)

```bash
git clone https://github.com/karjok/heimdall.git
cd heimdall

# Build signed release APK
./gradlew assembleRelease

# Or install directly to a connected test device
./gradlew installDebug
```

---

## 🔧 Sideloading Troubleshooting & Permissions

When installing an APK directly from a browser or file manager (outside an app store), Android may prompt standard security and permission checks:

### 1. Allow Unknown Apps (Chrome / Files)
- **Settings Path**: Phone **Settings** → **Install unknown apps** → Select **Chrome** or **Files** → Toggle on **Allow from this source**.
- *(From install prompt: Tap **Settings** on the warning popup → Turn on **Allow from this source** → Press **Back** → Tap **Install**).*

### 2. Google Play Protect Warning
- Tap **More details** → Tap **Install anyway** *(Messagate is 100% offline with zero internet permissions)*.
- *To pause scanning*: Open **Play Store** → Tap Profile icon (top right) → **Play Protect** → ⚙️ **Settings** (top right) → Turn off **Scan apps with Play Protect**.

### 3. Restricted Permissions (Android 13+)
If the SMS permission toggle is greyed out in App Info:
- Open phone **Settings** → **Apps** → **Messagate** → Tap **`⋮`** (top right) → Tap **Allow restricted settings** → Verify with fingerprint/PIN.

### 4. Set as Default SMS App
When Messagate launches, it automatically prompts:
- *"Set Messagate as your default SMS app?"* → Select **Messagate** → Tap **Set as default**.  
  This grants SMS interception access and loads your existing inbox.
