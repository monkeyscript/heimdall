# Heimdall 👁️🛡️

[![F-Droid](https://img.shields.io/f-droid/v/com.heimdall.app?logo=f-droid&color=blue)](https://f-droid.org/packages/com.heimdall.app/)
[![No Internet](https://img.shields.io/badge/Permissions-Zero%20Internet-brightgreen.svg)]()
[![GitHub Release](https://img.shields.io/github/v/release/monkeyscript/heimdall?label=GitHub%20Release&logo=github&color=gray)](https://github.com/monkeyscript/heimdall/releases/latest)

An Android SMS app that silently intercepts spam before it reaches your phone's message database—keeping your inbox clean even if you switch apps.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_inbox.png" width="31%" alt="Heimdall Inbox" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_message_modal.png" width="31%" alt="Message Inspector" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_settings.png" width="31%" alt="Spam Rules & Settings" />
</p>

## 🎭 How It Works

Most spam blockers just move spam into a folder—it still lives in your phone's SMS database, shows up if you switch messaging apps, and gets backed up to the cloud.

Heimdall drops spam **before** it is ever saved. When a message matches your blocklist, it is silently discarded—no notification, no vibration, and it **never touches the system SMS database**. Real messages pass through normally. If you ever switch back to Google Messages, your inbox is already clean.

## ✨ Features

- 🛡️ **Pre-Storage Spam Drop** — Blocked messages never reach Android's SMS database or wake your screen.
- 🔒 **100% Offline** — Zero internet permission. No data ever leaves your device.
- 🎯 **Custom Keyword Rules** — Block any word or phrase, in any language, with built-in anti-evasion filters.
- 🔑 **1-Tap OTP Copy** — Copies verification codes from notifications with a single tap and auto-dismisses the alert.
- 🏷️ **Smart Tags** — Organizes clean messages into Bank, Card, Delivery, Travel, and OTP categories.

> 📖 For a detailed feature breakdown and comparison with other apps, see the [Features & Comparison Guide](docs/features.md).

## 📦 Installation

Install via F-Droid for seamless setup and automatic updates:

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="75">](https://f-droid.org/packages/com.heimdall.app/)

> 📖 Looking for direct APK downloads, Obtainium, or building from source? See the [Installation Guide](docs/installation.md).

## 💡 Feedback & Feature Requests

Have a suggestion or found a bug? Please [open an issue](https://github.com/monkeyscript/heimdall/issues)—feedback and contributions are always welcome!
