# ChapterLock — Material You Expressive Focus & App Time-Budget Blocker

ChapterLock is a zero-ad, zero-subscription ADHD reading companion and distraction app blocker built with an ultra-bold, flat Google Material You Expressive design system. It combines two core modes: **Reading Lockdown Sessions** (locking distracting apps while you read a novel) and **Per-App Daily Time Budgets** (setting a custom minute limit for apps like Instagram or YouTube and intercepting them with a high-friction cognitive challenge when time runs out).

## User Review & Critical Decisions

> [!IMPORTANT]
> The plan incorporates your confirmed preferences from our design alignment. Review the summary below and click **Proceed** to begin building the application.

- **Confirmed Decision 1 — Friction Challenge Barrier**: Trying to open a locked app during a reading session or after exhausting its daily minute allowance triggers a full-screen Material You Expressive **Friction Shield** requiring a cognitive challenge (typing a custom mindfulness/novel-focus phrase or solving a multi-step arithmetic puzzle) before granting temporary access or aborting a lock.
- **Confirmed Decision 2 — Dual Blocking Engine (Per-App Daily Timers + Reading Sessions)**: Beyond locking apps during active novel reading sessions, you can assign individual daily minute budgets (e.g., 15m for Instagram, 30m for YouTube) to any installed app on your device, track real foreground usage via Android's `UsageStatsManager`, and automatically block apps once their timer hits zero.
- **Confirmed Decision 3 — Flat Dynamic Material You Expressive Aesthetic**: Uses Android 12+ System Dynamic Wallpaper Colors (`dynamicDarkColorScheme` / `dynamicLightColorScheme`) paired with oversized expressive typography, bold geometric shapes (28dp–36dp squircle cards, pill badges, asymmetric split blocks), and **strictly flat solid tonal surfaces with zero gradients** and zero filler/status fluff.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Per-App Distraction Timers ("App Vault")**: Scans installed apps on the device (and provides quick-add presets for major distraction apps like Instagram, YouTube, TikTok, X, Reddit) so you can set a strict daily minute allowance per app (e.g., 20 mins/day) or an immediate timed lock.
  2. **Novel Reading Lockdown ("Read Mode")**: Lets you pick the novel you are currently reading, set a reading sprint timer (e.g., 25, 45, or 60 minutes), and immediately lock all flagged distraction apps while tracking pages/chapters read.
  3. **Real Android Usage & Interception Engine**: Uses Android `UsageStatsManager` and a real foreground monitoring service / overlay interceptor to detect when a blocked or time-exhausted app is opened, immediately presenting the **Friction Challenge Shield**.
- **Target Audience / Persona**: Readers with ADHD who want to immerse themselves in novels without reflexively switching to short-form video or social feeds, and who want a genuinely functional, bold tool free of paywalls, dark patterns, or visual clutter.
- **Key Value**: Replaces willpower with immediate, tactile visual structure and intentional cognitive friction—turning impulsive app-switching into a conscious choice.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Active Focus & Reading Hub (Home Tab)**:
   - Opens directly to an oversized, asymmetric **Hero Reading Card** showing your active novel, current page/total pages progress bar, and a one-tap **"LOCK APPS & READ"** trigger.
   - Displays a bold countdown dial when a Reading Lockdown is active, with quick page-bookmark logging when the session finishes and an emergency **"Unlock Early"** button gated behind the Friction Challenge.
2. **Distraction App Timers (App Vault Tab)**:
   - Lists installed user apps from `PackageManager` alongside instant filters for Social/Video apps (Instagram, YouTube, etc.).
   - Each app row features a bold flat tonal card where you can toggle **"Block During Reading"** and set a **Daily Minute Limit** (via a tactile expressive slider or quick-step buttons: `5m`, `15m`, `30m`, `45m`, `60m`).
   - Shows real-time minutes used today vs. remaining allowance using a thick, flat pill progress indicator.
3. **Friction Challenge Shield (Interceptor Modal / Overlay Screen)**:
   - Triggered whenever you attempt to bypass an active reading lock, extend an exhausted daily app timer (+5 min emergency pass), or launch a blocked app.
   - Offers two selectable or randomized high-friction tasks:
     - **Phrase Challenge**: Type an exact case-sensitive focus statement (disable copy-paste) such as *"I am choosing to pause my novel for a temporary scroll"*.
     - **Arithmetic Chain**: Solve 3 sequential two-digit mental math problems under a reset timer.
4. **System Permission Setup Banner (Functional & Contextual)**:
   - Clean, collapsible action banner that only appears if Android `PACKAGE_USAGE_STATS` (Usage Access) is not yet granted, deep-linking directly to `Settings.ACTION_USAGE_ACCESS_SETTINGS` so real foreground app time tracking works out of the box.

### Visual Identity & Theme

- **Aesthetic Direction**: **Google Material You Expressive on Steroids** — inspired by Android 16 Material 3 Expressive concepts: oversized display numerals, chunky asymmetric bento blocks, high-contrast flat tonal color blocking, and playful geometric shapes (pills, squircles, and notched cards).
- **Color Palette & Mood**:
  - Powered by **System Dynamic Material You Colors** (`MaterialTheme.colorScheme`) derived directly from the user's wallpaper on Android 12+, with a curated high-contrast expressive tonal fallback palette when dynamic color is unavailable.
  - **Strictly Flat Solid Surfaces (Zero Gradients)**: Contrast is achieved purely by pairing `primaryContainer`, `secondaryContainer`, `tertiaryContainer`, `surfaceContainerHighest`, and `inverseSurface` blocks side by side with crisp borders and bold typography.
  - **Zero Pointless Chrome**: No fake account avatars, no "online/offline" indicators, no notification bells, and no filler settings pages.
- **Typography & Hierarchy**:
  - **Display & Numerals**: Bundled local Google Font **Syne** / **Space Grotesk** (heavy, architectural, expressive numerals for timers and page counts at `48sp`–`72sp`).
  - **Editorial Novel Titles**: **Fraunces** or **Playfair Display** for book titles and chapter quotes.
  - **UI & Controls**: **Plus Jakarta Sans** for crisp, scannable labels and badges.
- **Component Styling & Layout**:
  - Extra-large corner radii (`28.dp` to `36.dp`) on primary cards juxtaposed with sharp pill tags (`50%` radius) for active timers.
  - Chunky `56.dp`–`64.dp` minimum height action buttons with bold typography and full Material ripple feedback.

### Interactive Feedback & Motion

- **Spring Physics**: Expressive spring-driven shape morphing when starting a Reading Lockdown (card corners morph and expand into the active lock state).
- **Tactile Haptics**: Uses `LocalHapticFeedback` on timer increments, lock activation, and friction challenge keystrokes.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Real `PackageManager` + `UsageStatsManager` Integration with In-App Interceptor**
  - *Chosen Approach*: Query real installed apps via `PackageManager` combined with pre-configured profiles for popular distraction apps (Instagram, YouTube, TikTok, X, Reddit, Chrome) so the app works richly even in a clean emulator with few third-party apps installed. Track real foreground time via `UsageStatsManager` and enforce locks via a foreground monitoring loop + Friction Challenge Shield.
  - *Why*: Ensures 100% functional app discovery and time tracking on real Android devices while guaranteeing immediate testability on the streaming emulator.
  - *Alternatives Considered*: Requiring Device Owner / MDM provisioning, which cannot be activated by regular users without ADB.
- **Decision 2: Local On-Device Room SQLite Database (`androidx.room`)**
  - *Chosen Approach*: Store all configured App Time Budgets, Novel Library & Page Progress, and Focus Session Logs locally using Room with Kotlin Flows.
  - *Why*: App package names, daily usage budgets, and active lock timers are inherently tied to the physical Android device and must execute instantaneously with zero network latency, zero login friction, and total privacy.
  - *Alternatives Considered*: Cloud database requiring sign-in, which adds unnecessary friction and irrelevant account states to a local device utility.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                        UI LAYER (Jetpack Compose)                         │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌───────────────────┐  │
│  │   Read & Lock Hub    │ │   App Vault & Timers │ │ Novel Shelf & Log │  │
│  │  (Active Sprint +    │ │  (Per-App Daily Mins │ │ (Current Book,    │  │
│  │   Current Novel Hero)│ │   + Reading Block)   │ │  Page Progress)   │  │
│  └──────────┬───────────┘ └──────────┬───────────┘ └─────────┬─────────┘  │
│             └────────────────────────┼───────────────────────┘            │
│                                      ▼                                    │
│             ┌────────────────────────────────────────────────┐            │
│             │        Friction Challenge Shield Modal         │            │
│             │   (Exact Phrase Typing / 3-Step Math Puzzle)   │            │
│             └────────────────────────┬───────────────────────┘            │
└──────────────────────────────────────┼────────────────────────────────────┘
                                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    VIEWMODEL & STATE LAYER (StateFlow)                    │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │                         ChapterLockViewModel                        │  │
│  │  • Active Lockdown Countdown Timer & Session State                  │  │
│  │  • Real-Time App Budget Usage Calculator & Limit Enforcement        │  │
│  │  • Friction Challenge Generator & Verification Engine               │  │
│  └───────────────┬─────────────────────────────────────┬───────────────┘  │
└──────────────────┼─────────────────────────────────────┼──────────────────┘
                   ▼                                     ▼
┌────────────────────────────────────┐ ┌────────────────────────────────────┐
│     ANDROID SYSTEM TELEMETRY       │ │    LOCAL PERSISTENCE (Room DB)     │
│  • PackageManager (Installed Apps) │ │  • AppTimerRuleEntity (App Limits) │
│  • UsageStatsManager (Foreground   │ │  • NovelEntity (Books & Pages)     │
│    Minutes Used Today & Detection) │ │  • FocusSessionEntity (Read Logs)  │
└────────────────────────────────────┘ └────────────────────────────────────┘
```

### Data Model & State

1. **`AppTimerRuleEntity` (Per-App Distraction Rules)**:
   - `packageName` (Primary Key), `appName`, `category`, `dailyLimitMinutes` (0 = unlimited, >0 = daily timer cap), `usedMinutesToday`, `blockDuringReading` (Boolean), `isTemporarilyUnlockedUntil` (Epoch millis).
2. **`NovelEntity` (Reading Tracker)**:
   - `id`, `title`, `author`, `currentPage`, `totalPages`, `isCurrentlyReading` (Boolean), `minutesReadTotal`.
3. **`FocusSessionEntity` (Completed Reading Sprints)**:
   - `id`, `novelId`, `novelTitle`, `durationMinutes`, `pagesRead`, `completedAtTimestamp`, `wasInterrupted` (Boolean).

### Interactive Component & State Mapping

- **Starting a Reading Lockdown**:
  - User selects duration (`15m`, `30m`, `45m`, `60m`, `90m`) and taps **"LOCK APPS & READ"** -> ViewModel transitions `activeSessionState` to `Running`, starts a coroutine ticker, and activates interception for all apps marked `blockDuringReading = true` or over their `dailyLimitMinutes`.
- **Configuring Per-App Daily Timers**:
  - User opens **App Vault**, selects an installed or preset app (e.g., Instagram, YouTube), adjusts the daily minute limit slider (`5m`–`120m`) or toggles **Block in Read Mode** -> Persisted immediately via `Room` DAO; UI recomposes with updated remaining minutes badge.
- **Simulating / Triggering App Interception & Friction Unlock**:
  - When `UsageStatsManager` detects a locked/over-budget app in the foreground—or when the user taps **"Test Lock / Open App"** on a restricted app or **"Emergency Unlock"** during a reading sprint—the **Friction Challenge Shield** opens.
  - User must complete the exact phrase or 3-step math puzzle; only upon 100% validation does the ViewModel grant a 5-minute snooze or end the session early.
- **Completing a Reading Sprint**:
  - When the timer finishes (or user logs progress), an expressive **Log Pages Read** sheet updates `NovelEntity.currentPage` and records a `FocusSessionEntity`.
