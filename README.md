# PulseGuard

**An honest protection maintainer & watchdog for no-root Xiaomi HyperOS.**

On Xiaomi HyperOS / MIUI (and other aggressive China ROMs), delayed notifications aren't fixed by a
background "poke" — they're fixed by the **per-app OS settings**: Autostart, battery no-restrictions,
background execution, and notifications all being right. PulseGuard's job is to **check those, fix
the ones it can** (via [Shizuku](https://shizuku.rikka.app/) — no root), **guide you through the ones
it can't, and keep them in place** — warning you the moment they lapse. It doesn't pretend to be a
magic notification fix, because nothing without root is.

---

## What it does

- **Protection dashboard (home)** — for each app you choose, it checks every protection layer it can
  read (Doze whitelist, background execution, notifications) and shows ✅ / ⚠️. Red items it can fix
  get a one-tap **Fix**; layers it *can't* read (MIUI Autostart, background pop-up) are honest
  **Verify** steps that deep-link to the exact settings page — never a faked status.
- **Watchdog** — tracks Shizuku's binder in real time (binder-received / binder-dead listeners plus a
  periodic check). When it drops (typically after a reboot) it pauses and posts a high-priority
  **"Protection paused — reactivate Shizuku"** reminder; when Shizuku returns it auto-resumes and
  **reapplies** the fixable settings. It re-verifies periodically and reapplies any protection that
  silently lapsed after an update.
- **Delivery tracker (optional)** — grant Notification access and each app shows when it *last
  received a notification*, so silent failures surface. Records only package + time, never content.
- **Background poke (minor supplement)** — a low-key periodic `deviceidle tempwhitelist` runs too,
  but it's a supplement, not the fix. Don't rely on it.

## The honest limits

The only *complete* fixes for a China-ROM device are **rooting** it (so protections can't be
stripped) or switching to the **Global / EU ROM** (far less aggressive). PulseGuard is the best a
no-root China-ROM setup can do — and it says so, in-app, on the **"How PulseGuard works"** screen.

---

## How it works

A foreground service hosts the watchdog and, on a self-rescheduling exact alarm (default every
15 min; 5 / 10 / 15 selectable), re-verifies each protected app via Shizuku's shell UID and reapplies
anything fixable — for example:

```
dumpsys deviceidle whitelist                       # read Doze-exempt apps
cmd appops get <pkg> RUN_ANY_IN_BACKGROUND         # read background state
cmd deviceidle whitelist +<pkg>                    # (fix) add to the Doze whitelist
cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow   # (fix) allow background
cmd deviceidle tempwhitelist -d 60000 <pkg>        # minor supplement poke
```

On reboot, a `BOOT_COMPLETED` receiver re-arms the alarm and immediately surfaces the "reactivate
Shizuku" reminder — Shizuku itself needs reactivation after a reboot unless the device is rooted.

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

### 4. Run the checks and fix each app
On the **Protection** dashboard, tap **Run all checks**. For each app it reads (via Shizuku) the
Doze whitelist, background execution, and notification state. Anything red it can fix gets a one-tap
**Fix**; the layers it *can't* read — MIUI **Autostart**, **background pop-up**, per-app battery
**no-restrictions** — appear as **Verify** steps that deep-link to the exact settings page. Verify
those manually; PulseGuard never claims to detect Autostart.

### 5. Turn on background maintenance
On the dashboard, flip the toggle on. The watchdog then keeps watching Shizuku and re-verifies your
protections in the background. Grant the notification permission and, if prompted, the exact-alarm
permission.

### 6. (Optional) See delivery proof
Grant **Notification access** from the dashboard and each app shows when it last received a
notification — package name and time only, never any content.

---

## Screens

- **Protection** (home) — the dashboard: a **Protected / Protection paused** watchdog banner, the
  background-maintenance toggle, per-app protection cards (readable layers with **Fix**, manual layers
  with **Verify**, and "last notification: Xm ago"), **Run all checks**, and a link to the honesty
  screen.
- **Apps** — pick the apps to protect; selection is persisted.
- **Battery** — a transparent estimate of PulseGuard's own (small) cost, a best-effort
  `dumpsys batterystats` read, and a deep-link to the system battery screen.
- **Latency** — fires a real test notification through the alarm + notification pipeline and measures
  delivery latency.
- **Settings** — re-verify interval, battery-saver skips, night window, the supplement poke window,
  per-app management, Shizuku setup, and **How PulseGuard works**.
- **How PulseGuard works** — the honesty screen: what it does, what it can't, and that root or the
  Global/EU ROM are the only complete fixes.

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
├─ data/        DataStore-backed SettingsRepository, AppRepository, NotificationLogRepository,
│               ProtectionStateRepository, models
├─ engine/      KeepAliveService (FGS), AlarmScheduler (exact + Doze), ShizukuWatchdog,
│               ProtectionMonitor (re-verify + reapply), HealthChecker (read/fix + manual layers),
│               PulseEngine (supplement poke), PulseAlarmReceiver, BootReceiver,
│               PulseNotificationListener, BatteryInspector, LatencyTester
├─ ui/          Compose Navigation, Protection dashboard (home), Limitations screen,
│               per-feature screens + AndroidViewModels, Material 3 theme
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
