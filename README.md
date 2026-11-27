# Messagate 👁️🛡️

[![No Internet](https://img.shields.io/badge/Permissions-Zero%20Internet-brightgreen.svg)]()
[![GitHub Release](https://img.shields.io/github/v/release/karjok/heimdall?label=GitHub%20Release&logo=github&color=gray)](https://github.com/karjok/heimdall/releases/latest)

An Android SMS app that silently intercepts spam before it reaches your phone's message database—keeping your inbox clean even if you switch apps.

<p align="center">
  <img src="docs/images/1_inbox.png" width="31%" alt="Messagate Inbox" />
  <img src="docs/images/2_message_modal.png" width="31%" alt="Message Inspector" />
</p>

## 🎭 How It Works

Most spam blockers just move spam into a folder—it still lives in your phone's SMS database, shows up if you switch messaging apps, and gets backed up to the cloud.

Messagate drops spam **before** it is ever saved. When a message matches a rule, it is silently discarded—no notification, no vibration, and it **never touches the system SMS database**. Real messages pass through normally. If you ever switch back to Google Messages, your inbox is already clean.

## ✨ Features

- 🛡️ **Pre-Storage Spam Drop** — Blocked messages never reach Android's SMS database or wake your screen.
- 🔒 **100% Offline** — Zero internet permission. No data ever leaves your device.
- 🎯 **Custom Detection Rules** — Ordered rules with literal text, whole words, prefixes, exact matches or regex, matched against the message body, the sender ID, or both.
- ✅ **Allow-list & Mute** — Whitelist trusted senders (always evaluated first) or deliver messages without a notification.
- ⚡ **Quick Block** — One field in Settings blocks a word or phrase in the sender *or* body; flip it to regex mode when needed.
- 🔑 **1-Tap OTP Copy** — Copies verification codes from notifications with a single tap and auto-dismisses the alert.
- 💾 **Backup & Restore** — Export your rules and switches to a JSON file and import them on another device.
- 🏷️ **Smart Tags** — Organizes clean messages into Bank, Card, Delivery, Travel, and OTP categories.

> 📖 For a detailed feature breakdown and comparison with other apps, see the [Features & Comparison Guide](docs/features.md).

## 📦 Installation

Download the latest signed APK from GitHub Releases:

➡️ **[Download Latest Release](https://github.com/karjok/heimdall/releases/latest)**

> 📖 Looking for automatic updates via Obtainium, building from source, or sideloading troubleshooting? See the [Installation Guide](docs/installation.md).

## 💡 Feedback & Feature Requests

Have a suggestion or found a bug? Please [open an issue](https://github.com/karjok/heimdall/issues)—feedback and contributions are always welcome!
