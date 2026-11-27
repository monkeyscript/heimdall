# Features & Comparison Guide

---

## ⚖️ How Messagate Compares

| Feature | Messagate | Standard FOSS SMS (Fossify / QKSMS) | Notification Filters (Buzzkill) | Proprietary Apps (Google Messages / Truecaller) |
| :--- | :---: | :---: | :---: | :---: |
| **Silent Pre-Storage Drop** *(Never touches system SMS DB)* | ✅ | ❌ *(Stored in DB)* | ❌ *(Dismisses alerts only)* | ❌ *(Stored in DB)* |
| **100% Offline (No Internet Permission)** | ✅ | ✅ | ✅ | ❌ *(Requires network/cloud)* |
| **Auto 30-Day Spam Purge** | ✅ | ❌ *(Manual cleanup)* | ❌ | ❌ |
| **1-Tap OTP Copy & Auto-Dismiss** | ✅ | ❌ | ⚠️ *(Requires custom rules)* | ⚠️ *(Varies by OEM)* |
| **Keyword-Based Message Tags** | ✅ *(On-Device)* | ❌ | ❌ | ⚠️ *(Cloud-based / OEM specific)* |
| **No Account / No Telemetry** | ✅ | ✅ | ✅ | ❌ |

---

## 🎭 How Interception Works

Most Android spam blockers move spam into a "Spam" folder. The messages are still stored in your phone's central SMS database, reappear if you switch messaging apps, and get included in cloud backups.

Messagate works differently:
1. When set as your default SMS app, Messagate intercepts incoming messages before Android writes them to the system database.
2. If a message matches your blocklist, it is dropped silently—no vibration, no sound, and it is **never written to the Android SMS database**.
3. Real messages (bank alerts, personal texts, OTPs) are written to the system database normally.
4. If you ever switch back to Google Messages or Samsung Messages, your inbox stays clean because the spam was never stored on your phone in the first place.

---

## ✨ Feature Details

### 🎯 Custom Keyword Rules
- Add any words, sender IDs, or phrases to your blocklist from Settings.
- Works across all languages and Unicode scripts.
- Editing your rules re-evaluates your existing inbox instantly.

### 🛡️ Anti-Evasion Defense
Spammers often try to bypass keyword filters by inserting invisible zero-width spaces or formatting characters between letters (e.g., `"l​o​a​n"`). Messagate automatically strips these hidden characters before matching, preventing filter evasion.

### 📦 Stacked Sender Threads
Transactional senders (telecom recharges, delivery updates, OTP bursts) often flood your inbox with consecutive messages. Messagate groups consecutive messages from the same sender into compact, expandable cards with clean tree connectors.

### 🔑 1-Tap OTP Copy
Incoming verification codes and one-time passwords are automatically recognized. An actionable **"Copy [CODE]"** button appears directly on the notification—tapping it copies the code and dismisses the alert immediately.

### 🏷️ Smart Message Categories
Incoming clean messages are automatically tagged with visual labels:
- **Bank** — Account alerts and debit/credit notices.
- **Card** — Card swipe and transaction updates.
- **Delivery** — Shipping and courier tracking.
- **Travel** — Flight, train, and hotel reservations.
- **OTP** — Two-factor authentication codes.

*(Categorization runs 100% on-device for visual organization and does not affect blocking).*

### 🗑️ Auto 30-Day Spam Purge
Blocked spam kept inside Messagate is automatically purged after 30 days, keeping your storage clean without manual cleanup.

---

## 💬 Note on Sending SMS & RCS

Messagate is built strictly for reading and filtering incoming messages, not composing new ones:
- **No Outbound SMS / RCS**: Messagate does not send text messages.
- **Switching Back**: When you need to send a message, open Messagate's **Settings** and tap **"Switch Default App"** to return to Google Messages. All your clean messages are already there.
