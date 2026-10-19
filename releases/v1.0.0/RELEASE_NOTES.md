# LockIN v1.0.0 Release Notes

## Highlights

* **Real-Time Single-App Focus Lock & App-Switching Interception**: Instant window barrier (`LockWindowOverlayManager`) using `TYPE_ACCESSIBILITY_OVERLAY` and `TYPE_APPLICATION_OVERLAY` paired with sub-millisecond evaluation in `ChapterLockEnforcer`.
* **Live Foreground Usage Tracking**: Accurate daily screen time tracking via `UsageEvents` (`ACTIVITY_RESUMED` and `ACTIVITY_PAUSED`), including currently open foreground app sessions.
* **Deeply Integrated Notification System**:
  * Live hardware-backed downward countdown chronometer during Focus Lock sessions with **Open App**, **+15m Extend**, and **Unlock (Puzzle)** actions.
  * Automatic 80% daily budget heads-up alerts with a 1-tap **Lock App Now** action.
  * 100% daily limit reached alerts and Focus Lock completion notifications with **+15m Bonus Focus** actions.
* **Subtle Android 17/18 Spatial Physics & Haptic UX**: Cohesive spring specifications (`LockInMotion`), tactile press scale modifiers with light haptic feedback, directional tab transitions, and animated numeric counters.
* **Connected Multi-Screen Workflow**: Live active Focus Lock banner on the App Limits screen, +15m session extension controls across phone and tablet layouts, and 1m/5m/10m rapid presets with fine-grained minute steppers.

## Multi-ABI Release APKs

| Architecture | File | SHA-256 Checksum |
| :--- | :--- | :--- |
| **ARM64 (`arm64-v8a`)** | `LockIN-v1.0.0-arm64-v8a.apk` | `a6e98314e24f2945f03ec1c08e4602c2bf47bb3df3a8e6d250c4df7befce9837` |
| **ARMv7 (`armeabi-v7a`)** | `LockIN-v1.0.0-armeabi-v7a.apk` | `a8ed26c30aa6d1954e623d3f672a1da6f88de790c45bee1013838676787f4017` |
| **x86_64 (`x86_64`)** | `LockIN-v1.0.0-x86_64.apk` | `d31497a9d18da89844ddb0864b4f13cd0ecb0c5090adae8b73f6832b2cf0278d` |
| **x86 (`x86`)** | `LockIN-v1.0.0-x86.apk` | `c865f4b50be1a22ca82f5961952880dce4afbc1f8d320fad1579ce5a47c1629d` |
| **Universal (`all ABIs`)** | `LockIN-v1.0.0-universal.apk` | `678543653ae28822b083ead1e8ad33dc4de8a95e49e58b36d6300fb7c8037ea5` |
