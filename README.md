# PulseGuard

**Fix delayed notifications on aggressive battery-management ROMs — no root.**

Xiaomi HyperOS / MIUI (and other aggressive OEM ROMs like colorOS, Funtouch, and some Samsung
builds) freeze background apps so hard that push notifications arrive minutes — or hours — late.
PulseGuard periodically wakes the apps *you* choose, so their push connection stays alive and
notifications land on time. It uses [Shizuku](https://shizuku.rikka.app/) for privileged,
no-root shell access.

---

## What's new in v1.1.0

- **Shizuku reboot watchdog** — PulseGuard notices the moment Shizuku drops (typically after a
  reboot) and posts a distinct **"Protection paused — Shizuku needs reactivation"** reminder that
  deep-links to the setup wizard or opens the Shizuku app. When Shizuku returns, protection resumes
  automatically. Home shows a prominent **Protected** vs **Protection paused** banner.
- **Notification delivery tracker** — optionally grant Notification access and the dashboard shows
  when each protected app **last received a notification** — your proof it's working. Privacy-first:
  it records only the package name and time, never any notification content. Fully optional.
- **One-tap health fixes** — the Health screen adds a **Run all checks** button and, where Shizuku
  allows, a **Fix automatically** button that applies the fix (Doze whitelist, background execution,
  notification permission) and re-checks on the spot.

---

## How it works

On a self-rescheduling exact alarm (default every 10 minutes; 5 / 10 / 15 selectable), PulseGuard
runs — via Shizuku's shell UID — for each selected app:

```
cmd deviceidle tempwhitelist -d 60000 <pkg>   # ~60s window to reconnect its push socket
am set-standby-bucket <pkg> active            # loosen App Standby restrictions
cmd app_hibernation set-state --global <pkg> false   # best-effort un-hibernate
```

A foreground service keeps the process resident (so the Shizuku binding stays warm), while the
`setExactAndAllowWhileIdle` alarm guarantees wake-ups even in Doze. On reboot, a
`BOOT_COMPLETED` receiver re-arms everything. A **watchdog** tracks Shizuku's binder in real time
(binder-received / binder-dead listeners plus a periodic check): if it drops, PulseGuard pauses and
reminds you; when it returns, protection resumes automatically.

**Battery savers** are baked in and toggleable: skip the tick while the screen is on (default on),
while charging (default **off** — many people charge overnight, exactly when aggressive ROMs delay
notifications), or when idle on unmetered Wi-Fi (default off); and ease off (double the interval) at
night.

### Why not WorkManager?
Periodic WorkManager has a hard 15-minute floor and is itself deferrable under Doze — too slow
and too unreliable for this job. PulseGuard uses exact alarms instead.

---

## Setup

### 1. Install PulseGuard
Build & install the debug APK (see *Building* below) or sideload a release build.

### 2. Set up Shizuku (one-time, ~3 minutes)
PulseGuard's in-app **Shizuku setup wizard** walks you through this with live status, but in short:

1. **Install Shizuku** — Shizuku isn't on the Play Store, so download and **sideload** the APK
   from the [GitHub releases](https://github.com/RikkaApps/Shizuku/releases) or
   [F-Droid (IzzyOnDroid)](https://apt.izzysoft.de/fdroid/index/apk/moe.shizuku.privileged.api).
   You may need to allow "install unknown apps" for your browser/file manager. The wizard
   auto-detects `moe.shizuku.privileged.api` once it's installed.
2. **Enable Developer options** — Settings → About phone → tap **Build number** 7 times.
3. **Enable Wireless debugging** — Settings → System → Developer options → **Wireless debugging** (on).
4. **Start Shizuku** — open the Shizuku app and start it via **Wireless debugging** pairing
   (Shizuku has its own guided flow; no computer required on Android 11+).
5. **Grant PulseGuard access** — back in PulseGuard, tap **Grant permission** and approve the
   Shizuku prompt. The status pill turns **green ✅** when connected.

> ⚠️ **After every reboot, Shizuku must be re-activated** (step 4) unless your device is rooted.
> This is a Shizuku limitation, not PulseGuard's — a non-rooted Shizuku service does not survive
> a reboot. PulseGuard detects when Shizuku is down and shows a notification reminding you to
> restart it. (On a rooted device, Shizuku can auto-start and this step is unnecessary.)

### 3. Choose your apps
Open **Apps** and tick the apps whose notifications arrive late (messengers, email, etc.).

### 4. Turn on protection
On **Home**, flip **Protection** on. Grant the notification permission and, if prompted, the
exact-alarm permission when asked.

### 5. (Recommended) Per-app system tweaks
Open **Health** and tap **Run all checks**. For each app it checks (via Shizuku) the
battery-optimization exemption, background execution, and notification state. Anything that's off
gets a one-tap **Fix automatically** (applied via Shizuku, then re-checked) or a **Fix in Settings**
deep-link. On Xiaomi, also enable **Autostart** for each app — this can't be read by any app, so
PulseGuard guides you to the Autostart settings rather than pretending to detect it.

### 6. (Optional) See delivery proof
Grant **Notification access** from the Home dashboard and PulseGuard will show when each protected
app last received a notification. It stores only the package name and time — never any content.

---

## Screens

- **Home** — a **Protected / Protection paused** banner, protection on/off, last/next pulse, a
  per-app **notification-delivery** tracker ("last notification: Xm ago"), quick actions, and any
  warnings (Shizuku down, exact alarms off, no apps selected).
- **Apps** — pick the launchable apps to keep alive; selection is persisted.
- **Health** — **Run all checks**, per-app green/red checks via Shizuku, one-tap **Fix
  automatically** for shell-fixable items plus **Fix in Settings** deep-links, and Autostart guidance.
- **Battery** — a transparent estimate of PulseGuard's own cost (pulses/day × per-pulse cost),
  plus a best-effort `dumpsys batterystats` read and a deep-link to the system battery screen.
- **Latency** — fires a real test notification through the alarm + notification pipeline and
  measures delivery latency, so you can *see* it working.
- **Settings** — interval, battery-saver toggles, night window, reconnect window, per-app
  management, and Shizuku setup.

---

## Honesty notes / known constraints

- **MIUI/HyperOS Autostart is not readable** via any public API. PulseGuard therefore *guides*
  you to the Autostart page instead of claiming to detect its state.
- **Without Shizuku**, PulseGuard degrades to a **guided-only** mode: it can't run the keep-alive
  commands or live health checks, but every relevant system page is one deep-link away.
- **A clean per-app battery mAh** figure isn't reliably exposed across ROMs, so the Battery screen
  leads with a transparent model and treats the raw `batterystats` read as supporting detail.
- **Foreground service type** is `specialUse` (Android 14+) — no standard FGS category fits a
  cross-app push keep-alive, and the manifest declares the honest subtype.

---

## Architecture

Clean separation across layers, single-activity + Jetpack Compose + MVVM:

```
com.pulseguard
├─ shizuku/     IUserService.aidl, ShellUserService (Runtime.exec as shell), ShizukuManager
├─ data/        DataStore-backed SettingsRepository, AppRepository, NotificationLogRepository, models
├─ engine/      KeepAliveService (FGS), AlarmScheduler (exact + Doze), PulseEngine (tick),
│               PulseAlarmReceiver, BootReceiver, ShizukuWatchdog, PulseNotificationListener,
│               HealthChecker (+ auto-fix), BatteryInspector, LatencyTester
├─ ui/          Compose Navigation, per-feature screens + AndroidViewModels, Material 3 theme
└─ util/        DeepLinks (system/OEM settings), TimeFormat
```

- **Kotlin**, Jetpack **Compose** + **Material 3**, **Compose Navigation**, **Coroutines/Flow**,
  **DataStore** for persistence.
- **Shizuku**: `dev.rikka.shizuku:api` + `:provider`; a UserService (AIDL) executes shell
  commands via `Runtime.exec`, running with the shell UID Shizuku grants.
- **minSdk 30**, targetSdk 36.

---

## Building

Requires JDK 17+ (the project targets AGP 9 / Gradle 9.4). Android Studio's bundled JBR (21)
works well.

```bash
# From the project root
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Install with `adb install -r <apk>` or run from
Android Studio.

---

## Privacy

PulseGuard runs entirely on-device. It has no network permission of its own, collects nothing,
and sends nothing anywhere. Shizuku access is used only to run the keep-alive and diagnostic
shell commands described above.

The optional **notification-delivery tracker** uses a `NotificationListenerService` (which requires
you to grant Notification access). Even then it records **only** the package name and the time a
notification arrived for your protected apps — never the title, text, or any other content — stored
locally in DataStore. The tracker is entirely optional; PulseGuard works fully without it.
