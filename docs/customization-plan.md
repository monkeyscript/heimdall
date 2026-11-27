# Heimdall — Full Customization Plan (Detection & Organization)

Status: design proposal · branch `feat/advance-features` · based on v1.0.6 (versionCode 7)

> **Implementation status:** Phase 0 + Phase 1 are implemented (ordered rule engine with
> CONTAINS/WORD/PREFIX/SUFFIX/EXACT/REGEX, BODY/SENDER/BOTH, BLOCK/ALLOW/TAG/MUTE,
> priority ordering, migration of legacy keywords, Rules editor with test bench) and the
> long-press gesture now covers the whole message row. The app is now branded **Messagate**
> (`com.messagate.app`). Settings has a QUICK BLOCK box (TEXT/REGEX, sender+body) and shows
> every rule as a deletable chip. Phase 2 (user-defined categories) and Phase 4
> (select-all / bulk actions) are pending; Phase 3 import/export is implemented for
> rules + switches (JSON file, messages excluded).

---

## 1. How the app works today (complete flow)

### 1.1 Incoming SMS pipeline

```
Telephony (SMS_DELIVER when default SMS app, else SMS_RECEIVED)
        │
        ▼
SmsReceiver.onReceive()                        app/receiver/SmsReceiver.kt:20
  ├─ drop duplicate SMS_RECEIVED if default    :30
  ├─ read master switch                        :36
  ├─ concatenate multipart body                :42-46
  ├─ normalize body                            :47
  │    fullBody.replace(Regex("[\\p{Cf}\\u200B-\\u200D\\uFEFF]"), "").lowercase()
  │
  ├─ MASTER OFF → (if default) write to Telephony provider,
  │                log as clean, show notification, return        :53-84
  │
  ├─ SPAM FILTER ON ?                                            :86-101
  │    for (kw in userKeywords)
  │        if (normalizedBody.contains(kw)) → isSpam, matchedKeyword
  │    (first match wins, order = SharedPreferences StringSet order, nondeterministic)
  │
  ├─ if isSpam → incrementBlockedCount()                         :103-105
  │
  ├─ CategoryHelper.detectCategory(sender, body, isSpam)         :108
  │    hardcoded priority: SPAM → DELIVERY → OTP → TRAVEL → CARD → BANK → CHAT
  │
  ├─ prefsManager.addInspectedMessage(InspectedMessage(...))     :115-124
  │    in-memory list + async full-JSON rewrite to SharedPreferences
  │
  └─ isDefault?
       ├─ spam   → silent drop (never written to system SMS DB)  :128-133
       └─ clean  → write to Telephony provider + notification    :134-147
     not default → companion notification with spam/clean state  :148-158
```

### 1.2 Notification / OTP path

```
NotificationHelper.showInspectionNotification()   util/NotificationHelper.kt:51
  ├─ 2 fixed channels (high importance)           :28-47
  ├─ title ⚠️ spam / 🛡️ clean
  ├─ OtpHelper.extractOtp(body) → "Copy <code>" action
  │    3 hardcoded regexes + context keyword gate + year 2024..2026 skip
  └─ PendingIntent → MainActivity(EXTRA_MESSAGE_TIMESTAMP)

CopyOtpReceiver                                  receiver/CopyOtpReceiver.kt:16
  ├─ clipboard set
  ├─ cancel notification
  └─ mark message read
```

### 1.3 Storage / state

```
PreferencesManager                                data/PreferencesManager.kt:23
  ├─ SharedPreferences "messagate_prefs"
  │    master_active, filter_enabled, filtered_keywords (StringSet),
  │    show_spam_in_feed, blocked_count, inspected_logs (one JSON string, max 10 000)
  ├─ process-wide memoryMessagesCache + messagesVersion (Compose trigger)
  ├─ add/delete/read → full list rewrite via saveMessagesAsync (IO coroutine)
  ├─ rule change → triggerReevaluation(150ms debounce)
  │    → re-classify every stored message (spam + category) → rewrite JSON
  └─ cleanupOldSpam(): deletes spam older than 30 days, max once per 24 h
```

### 1.4 UI flow

```
MainActivity → HeimdallApp                         MainActivity.kt:188
  ├─ onboarding: force default-SMS role + POST_NOTIFICATIONS          :256-271
  ├─ one-time import of system inbox (up to 10 000)                   :283-300
  ├─ InboxScreen                                                      :525
  │    ├─ filter = hide spam (unless toggle) + unread + text search    :572-582
  │    ├─ groupContiguousMessages() stacks consecutive same-sender     :123-144
  │    ├─ LazyColumn, 25/page "SHOW MORE"                              :1002-1125
  │    └─ long-press → selection mode → DELETE (n) → confirm dialog    :549-551, 811-859, 605-689
  ├─ MessageDetailModal (category label, OTP copy, delete)             :1464
  └─ SettingsScreen                                                    :1691
       ├─ ACTIVE master switch
       ├─ ADVANCED → SPAM FILTER toggle
       ├─ SPAM RULES → add/remove literal keywords
       ├─ FEED PREFERENCES → show spam in feed
       └─ PURGE ALL SPAM
```

There is **no category filter, no category editor, no rule editor beyond a text field** — categories are display-only metadata.

---

## 2. Hardcoded inventory (what blocks customization)

| # | Location | Hardcoded today | Impact |
|---|---|---|---|
| 1 | `util/CategoryHelper.kt:3-10` | Exactly 7 categories, fixed enum, fixed emoji + short label (`SPAM/OTP/TRVL/PKG/CARD/BANK/MSG`) | Can't add "Government", "School", "Promo", rename or recolor |
| 2 | `util/CategoryHelper.kt:36-143` | ~40 English/India-specific phrases per category (`dcthln`, `delhivery`, `bluedart`, `irctc`, `fedbnk`, `hdfcbk`, `axisbk`, `via upi`, `pnr:` …) | Non-IN users / other languages get no categories; false positives can't be fixed by user |
| 3 | `util/CategoryHelper.kt:28-30` | Fixed priority order spam → delivery → OTP → travel → card → bank → chat; first hit wins | User cannot reorder or make a rule "tag only" without blocking |
| 4 | `SmsReceiver.kt:93-101`, `PreferencesManager.kt:117-147`, `SmsRoleHelper.kt:143-145` | Spam rule = plain `lowercase().contains()` on body only | No regex, no whole-word, no sender match, no AND/OR, no case control, no allow-list |
| 5 | `SmsReceiver.kt:103-105` | Every match = hard block (silent drop) | No "warn only / tag only / mute notification" action per rule |
| 6 | `util/OtpHelper.kt:4-11` | 3 fixed regexes; context gate `otp|code|verification|…`; 4-digit years 2024-2026 skipped | Can't support local OTP wording (e.g. "kode verifikasi") or fix false positives |
| 7 | `PreferencesManager.kt:45` | `DEFAULT_KEYWORDS = emptySet()` | Fresh install blocks nothing; no sensible starter rules |
| 8 | `PreferencesManager.kt:286` | Spam purge = 30 days, job max once/24 h | Not configurable |
| 9 | `PreferencesManager.kt:43` | 10 000 message cap | Not configurable; full JSON rewrite each change |
| 10 | `util/NotificationHelper.kt:15-47` | 2 fixed channels, HIGH importance, vibration on | Can't mute spam warnings or choose importance |
| 11 | `util/LinkifyHelper.kt:30-45` | TLD list + India-centric phone regex (+91, 1800/1900) | Links/phones in other regions not linkified |
| 12 | `MainActivity.kt:553-582` | Inbox filters: spam on/off, unread, text only | No category/sender/rule filters, no saved views |
| 13 | `MainActivity.kt:549-551` etc. | Selection only via long-press; group-level by default; no select-all | Multi-select is undiscoverable and limited (see §5) |

Also duplicated classification entry points that must be unified: `SmsReceiver`, `SmsRoleHelper.readExistingInboxMessages`, `PreferencesManager.reevaluateMessagesWithKeywords`, `CategoryHelper` consumers in UI.

---

## 3. Target design: a small, offline, deterministic rule engine

Principles: zero network, deterministic, fast enough for a BroadcastReceiver (< 5 ms for 1 KB body), crash-safe against bad regex, fully user editable, import/exportable.

### 3.1 Data model

```kotlin
enum class MatchField { BODY, SENDER, BOTH }
enum class MatchType { CONTAINS, WORD, PREFIX, SUFFIX, EXACT, REGEX, SENDER_ID }  // SENDER_ID = mobile shortcode/alpha
enum class RuleAction { BLOCK, ALLOW, TAG, MUTE }   // BLOCK = current silent drop

data class Rule(
    val id: String,                     // stable uuid for storage/migration
    val name: String,                   // shown in UI and in "matched rule" badge
    val enabled: Boolean = true,
    val priority: Int,                  // lower = evaluated first (drag & drop)
    val field: MatchField,
    val type: MatchType,
    val pattern: String,
    val caseSensitive: Boolean = false,
    val normalizeUnicode: Boolean = true,  // strip \p{Cf}, zero-width, homoglyph fold
    val categoryId: String? = null,     // for TAG
    val action: RuleAction,
    val stopProcessing: Boolean = true, // false = continue and allow later rules to add tags
    val createdAt: Long,
    val hits: Long = 0                  // analytics, optional
)

data class Category(
    val id: String,                     // stable slug, e.g. "bank"
    val label: String,                  // "Bank"
    val shortLabel: String,             // "BANK" (chip)
    val emoji: String,                  // "🏦"
    val color: Long,                    // 0xFFRRGGBB
    val priority: Int,                  // display order & default tag precedence
    val protected: Boolean = false,     // SPAM/CHAT cannot be deleted
    val builtin: Boolean = false
)
```

Storage (versioned JSON, single source of truth):

```json
{
  "schema": 2,
  "rules": [
    {"id":"r1","name":"Loan spam","enabled":true,"priority":10,
     "field":"BODY","type":"REGEX","pattern":"\\b(loan|kredit|pinjaman)\\b",
     "caseSensitive":false,"normalizeUnicode":true,"action":"BLOCK",
     "stopProcessing":true,"categoryId":null}
  ],
  "categories": [
    {"id":"bank","label":"Bank","shortLabel":"BANK","emoji":"🏦",
     "color":4282476152,"priority":20,"protected":false,"builtin":true}
  ],
  "settings": {"purgeDays":30,"maxMessages":10000}
}
```

Keys: `rule_engine_v2` (JSON), `categories_v2` (JSON). Keep `filtered_keywords` only for migration, then delete.

### 3.2 Evaluation semantics (must be written down, then unit-tested)

```
INPUT: sender, rawBody
1. normalized = TextNormalizer.normalize(rawBody)   // NFC, strip Cf, fold homoglyphs
2. rules = enabledRules.sortedBy(priority)
3. verdict = CLEAN, matched = [], categoryHint = null
4. for rule in rules:
     if rule.matches(sender, normalized):
        matched += rule
        when (rule.action):
          ALLOW -> return CLEAN (whitelist wins, overrides everything below)
          BLOCK -> verdict = SPAM; if stopProcessing break
          TAG   -> categoryHint = first non-null categoryId; if stopProcessing break
          MUTE  -> verdict = MUTE (deliver to DB, no notification)
        if rule.stopProcessing break
5. if verdict == CLEAN and categoryHint == null:
     categoryHint = categories.sortedBy(priority).firstOrNull {
        it.hasEnabledTagRules && matched any -> ... } // optional: category rules as TAG rules
   else default CHAT
6. persist InspectedMessage(isSpam, matchedRuleId, matchedRuleName, matchedText, categoryId)
```

Rules are **ordered**, that is the whole trick: it replaces the hardcoded if-chain with user-owned precedence. UI shows drag handles and "first match wins" copy.

Backward compatibility: on first launch of v2, convert each existing keyword into:

```json
{"name":"<keyword>","field":"BODY","type":"CONTAINS","pattern":"<keyword>","action":"BLOCK","priority":<n>}
```

`MessageCategory.fromString()` must keep resolving old strings (`"TRAVEL"`, `"DELIVERY"`, `"CARD"`, `"BANK"`, `"OTP"`, `"SPAM"`, `"CHAT"`) even if the user renames/deletes categories — keep a reserved builtin id per legacy name forever.

### 3.3 Safety & performance for regex (critical: broadcast receiver)

* Compile patterns once into a `CompiledRule` cache; invalidate on a prefs version bump (same `messagesVersion` pattern).
* Cap pattern length (256 chars) and body length considered (4 096 chars).
* Catch `PatternSyntaxException` → surface "invalid pattern" in the editor, never crash the receiver.
* ReDoS guard: evaluate regex on a bounded worker with a 50 ms budget; on timeout treat as **no match** and log (do not block the SMS). Also catch `StackOverflowError`.
* Never run rule evaluation on the main thread from UI; receiver already runs on the main looper → keep it synchronous but tiny (precompiled, ≤ 64 rules recommended; warn above that).
* Optional "test bench" runs the same engine against a pasted sample and reports per-rule match + elapsed ms.

### 3.4 Categories as data

* Builtin set ships as defaults (current 7), each with `builtin=true` so migration stays stable; user can rename, change emoji/color, reorder, disable (except SPAM/CHAT).
* Custom categories: "Government", "Promo", "Family", … any label + emoji + color.
* Category assignment = a TAG rule; ordering in `Category.priority` decides fallback when multiple tags match.
* Per-message override: long-press/section in the detail modal → change category (stores `categoryOverride = true`), and "Always categorize this sender" creates a `SENDER_ID` TAG rule in one tap.
* Inbox: category chips row (All / Unread / each category), computed from distinct categories in use, with counts; optional "hide empty categories".

### 3.5 Settings UX

```
SETTINGS
├─ ACTIVE
├─ ADVANCED
│   ├─ SPAM FILTER (existing)
│   └─ DETECTION ENGINE (new)
│        ├─ RULES            → list (drag to reorder, on/off, hit count)
│        │    └─ Rule editor: name, field (body/sender/both), type
│        │       (contains/word/prefix/exact/regex/sender id), pattern,
│        │       case-sensitive, strip invisible chars, action
│        │       (block/allow/tag/mute), category, stop-on-match
│        │       → "TEST" box: paste an SMS → verdict + which rule matched
│        ├─ CATEGORIES       → list (reorder, emoji, color, rename, add, delete)
│        └─ OTP PATTERNS     → list of regex + context keywords (replaces OtpHelper)
├─ FEED PREFERENCES
│   ├─ show spam in feed
│   ├─ spam purge days (off/7/30/90/forever)
│   └─ max stored messages
├─ NOTIFICATIONS → per-channel toggles (clean, spam, OTP action)
├─ IMPORT / EXPORT rules+settings as JSON (copy/paste or SAF file)
└─ PURGE ALL SPAM
```

Quick actions in the inbox/detail modal: **Block sender**, **Always allow sender**, **Create rule from this message**, **Change category**, **Explain classification**.

---

## 4. Multi-select deletion — current state and plan

What already exists (v1.0.6):

* Long-press a row or avatar → selection mode (`selectedTimestamps`), `MainActivity.kt:1045-1052`, `1145`, `1180-1191`.
* Tapping a collapsed group selects the **whole group** (`onToggleSelectGroup`); tapping an expanded sub-row toggles that single message (`onToggleSelectTimestamp`, `1375-1381`, checkbox at `1423-1444`).
* Contextual bar with `DELETE (n)` → confirmation dialog (`811-859`, `605-689`). Confirmed batch delete also removes clean messages from the system SMS DB (`MainActivity.kt:456-469`).

Gaps that likely caused "no multi selection on deletion":

1. **Discoverability** — nothing hints that long-press starts selection; no visible checkboxes, no "Select" entry in the options bar.
2. **No Select all / Clear** — impossible to bulk-delete a search result or a category with many messages without tapping each group.
3. **No range selection** — no shift-tap equivalent.
4. **Group granularity** — first long-press selects every message in the stacked group; selecting one sub-message requires expanding first.
5. **No bulk actions besides delete** — no bulk "move to spam", "mark read/unread", "allow sender", "change category".
6. **No undo** — deletion is immediate after dialog, including the central SMS DB.
7. **No category/sender filter** to scope a bulk delete.

Planned redesign:

* Options bar gets a **SELECT** button next to UNREAD; entering selection mode shows checkboxes on every row (collapsed groups show a tri-state checkbox).
* Sticky action bar: `Select all (n)` / `Clear`, `DELETE`, `READ`, `CATEGORY`, `⋮`.
* Select-all applies to the **current filtered set** (search/category/unread), which is what users expect; show "All 128 filtered" wording.
* Long-press still works; on a stacked group it selects the **head only** and offers "Expand to select individual messages" (safer default).
* Confirmation dialog groups counts: `N spam (never in system DB) · M clean (also removed from phone SMS storage)`.
* Optional 5-second undo snackbar (keep deleted items in memory; telephony deletion is harder to undo → do DB deletion after undo window or state clearly it's permanent).

---

## 5. Phased implementation plan

Estimates assume one developer familiar with the code; each phase ends with a shippable debug build.

### Phase 0 — Refactor without behavior change (0.5 day)

* Add `util/TextNormalizer.kt` (single normalization function, used by receiver, import, reevaluation).
* Add `core/engine/RuleEngine.kt` with `Rule`, `Category`, `DetectionResult`; wrap today's keyword logic as `CONTAINS/BLOCK` rules created from prefs.
* Add `core/engine/CompiledRuleCache.kt`.
* Route all four classification call-sites through `RuleEngine.evaluate()`.
* Add JVM unit tests: multipart, unicode zero-width, case, empty body, first-match-wins, 10k-message re-evaluation benchmark.
* **Acceptance:** identical classification results for existing users; receiver still < 5 ms typical.

### Phase 1 — Spam rule patterns (1–1.5 days)

* Rule types: CONTAINS, WORD, PREFIX, SUFFIX, EXACT, REGEX; fields BODY/SENDER/BOTH; case-sensitive flag; stop-on-match; enabled; priority.
* Actions: BLOCK, ALLOW (whitelist evaluated first), MUTE (deliver, no notification).
* Settings "RULES" screen: list, reorder, toggle, add/edit/delete, regex validation, live tester.
* Migration of `filtered_keywords` → rules (one-time, idempotent).
* Match metadata stored on the message (`matchedRuleName`, `matchedText`) and shown in the row badge.
* Seed a small optional starter pack (loan/prize/crypto/short-link) that the user can delete.
* **Acceptance:** a regex rule blocks without a keyword; an ALLOW rule overrides a BLOCK rule; invalid regex cannot crash the receiver; config survives reboot.

### Phase 2 — Custom categories (1.5 days)

* Category CRUD + reorder + emoji/color + protected builtins; `id`-based storage; legacy string migration.
* Category assignment via TAG rules; category priority fallback; per-message override.
* Inbox category chips + counts + combined filtering with search/unread.
* Detail modal: category picker and "create rule for this sender".
* Remove `MessageCategory` enum usage from storage/UI (keep a compat shim for old logs).
* **Acceptance:** create "Government", tag it with `SENDER_ID`/regex, filter inbox by it, restart app, still correct.

### Phase 3 — Organization extras (1 day)

* Import/export rules + categories as JSON (clipboard and SAF `CreateDocument`/`OpenDocument`, no network permission).
* "Explain classification" sheet: normalized text preview, ordered rules evaluated, matched rule, elapsed time.
* Optional boolean groups (AND/OR) if single-pattern rules prove insufficient — model `RuleGroup(operator, children)`; keep it out of v1 of the editor to avoid UI complexity.
* Configurable purge days and max stored messages; notification channel toggles.
* **Acceptance:** export → wipe → import reproduces identical verdicts on a corpus of sample SMS.

### Phase 4 — Multi-select & bulk operations (0.5–1 day)

* Select button, checkboxes, tri-state group select, select-all-filtered, clear.
* Bulk delete/read/category/allow-sender; grouped confirmation; optional undo.
* **Acceptance:** filter by category + search → select all → delete only those; clean messages removed from system DB, spam just from Heimdall log.

### Phase 5 — Storage & performance (0.5–1 day, optional but recommended)

* Replace "one JSON string in SharedPreferences" with either Room/SQLite or append-only JSONL + periodic compaction. Current design rewrites up to 10 000 messages on every add/delete/re-evaluation; that grows with rule-editing frequency.
* Debounce persistence, keep `memoryMessagesCache` as the hot path, background write with mutex.
* Benchmark: 10 000 messages import, rule toggle re-evaluation, cold start.

Total: roughly **5–6 focused days** for Phases 0–4.

---

## 6. Risks & constraints

| Risk | Mitigation |
|---|---|
| BroadcastReceiver time budget (`SMS_DELIVER` ~10 s, main thread) | precompiled rules, ≤ 64 rules, 50 ms regex watchdog, no disk I/O in `onReceive` |
| ReDoS / catastrophic regex | length caps, timeout, catch `StackOverflowError`, editor validation + tester |
| GitHub-only distribution, no internet permission | import/export via clipboard/SAF only; no telemetry; keep `dependenciesInfo` settings |
| Old logs with enum strings | permanent builtin id mapping + `categoryOverride` flag |
| Full-JSON rewrite cost at 10 000 msgs | Phase 5 storage change; until then debounce and skip rewrite when only read-state changes? (read state is part of JSON, so no) |
| Users creating conflicting rules | ordered list + first-match-wins UI + ALLOW-first semantics + "explain classification" |
| Release signing / versioning | bump versionCode, keep `proguard-rules.pro` and R8 rules for `org.json` reflection-free code (no Gson/Moshi to avoid new deps) |

### File-level change map

```
new  app/src/main/java/com/messagate/app/core/engine/Rule.kt
new  app/src/main/java/com/messagate/app/core/engine/Category.kt
new  app/src/main/java/com/messagate/app/core/engine/RuleEngine.kt
new  app/src/main/java/com/messagate/app/core/engine/CompiledRule.kt
new  app/src/main/java/com/messagate/app/core/engine/RuleRepository.kt      (JSON load/save/migrate)
new  app/src/main/java/com/messagate/app/core/engine/CategoryRepository.kt
new  app/src/main/java/com/messagate/app/util/TextNormalizer.kt
new  app/src/main/java/com/messagate/app/ui/settings/RulesScreen.kt
new  app/src/main/java/com/messagate/app/ui/settings/RuleEditorScreen.kt
new  app/src/main/java/com/messagate/app/ui/settings/CategoriesScreen.kt
new  app/src/main/java/com/messagate/app/ui/settings/ImportExportScreen.kt
new  app/src/test/java/com/messagate/app/engine/RuleEngineTest.kt
edit app/src/main/java/com/messagate/app/util/CategoryHelper.kt            (delete hardcoded logic → defaults only)
edit app/src/main/java/com/messagate/app/util/OtpHelper.kt                (patterns from config)
edit app/src/main/java/com/messagate/app/receiver/SmsReceiver.kt           (call engine)
edit app/src/main/java/com/messagate/app/data/PreferencesManager.kt        (message schema: matchedRule*, categoryId)
edit app/src/main/java/com/messagate/app/util/SmsRoleHelper.kt            (engine on import)
edit app/src/main/java/com/messagate/app/MainActivity.kt                  (settings nav, category chips, selection mode)
edit app/src/main/java/com/messagate/app/util/NotificationHelper.kt       (channel config)
```

---

## 7. Suggested next step

Start with Phase 0 + Phase 1 together (rules engine + custom patterns) because they unblock every other customization and preserve current behavior. Category CRUD then builds on the same engine without another refactor.
