---
name: android-sdk-event-inspection
description: Use when verifying SDK event payloads in the Android SDK sandbox, debugging what events are emitted, or validating request bodies via logcat with the sample Kotlin app.
---

# Datadog Android SDK — Event Inspection

## Overview

The Android SDK uses `CurlInterceptor` to log every outgoing HTTP request as a `curl` command. In debug builds, enabling `printBody = true` exposes the full JSON payloads sent to intake endpoints.

**Core principle:** Enable body logging BEFORE building, then rebuild + reinstall. Build-time config (`config/{flavor}.json`) is baked into `BuildConfig` — changes only take effect after a fresh build.

## When to Use

- Verifying a change emits the correct event type or fields
- Checking custom attributes appear on a log, trace, or RUM event
- Debugging why RUM/logs/traces are not showing up in Datadog
- Validating SDK behavior with the `sample/kotlin` app

## Setup

### 1. Enable Request Body Logging

Edit `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/CoreFeature.kt` line ~632:

```kotlin
// Before (body suppressed):
builder.addNetworkInterceptor(CurlInterceptor())

// After (full JSON payloads visible):
builder.addNetworkInterceptor(CurlInterceptor(printBody = true))
```

This change is gated by `BuildConfig.DEBUG` — it only runs in debug builds.

### 2. Configure Build-Time Credentials

Create `config/{flavor}.json` (e.g., `config/us1.json`) — values are injected into `BuildConfig` at compile time:

```json
{
  "token": "pub<32-hex-chars>",
  "rumApplicationId": "<uuid>",
  "apiKey": "<40-hex-chars>",
  "applicationKey": "<40-hex-chars>",
  "logsEndpoint": "https://browser-intake-datadoghq.com",
  "tracesEndpoint": "https://browser-intake-datadoghq.com",
  "rumEndpoint": "https://browser-intake-datadoghq.com",
  "sessionReplayEndpoint": "https://browser-intake-datadoghq.com"
}
```

Token format must be `pub` + 32 hex chars. Any UUID works for `rumApplicationId`.

### 3. Build and Install

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
./gradlew :sample:kotlin:installUs1Debug
```

### 4. Launch the App

```sh
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb

# Find the launcher activity (run once to confirm):
$ADB shell dumpsys package com.datadog.android.sample | grep -A2 "MAIN"
# → com.datadog.android.sample/.NavActivity

$ADB shell am start -n com.datadog.android.sample/.NavActivity
```

### 5. Stream Logcat

```sh
$ADB logcat -s "Datadog" "DD_LOG" "Curl" "*:S"
```

## Log Tags Quick Reference

| Tag | What it shows |
|---|---|
| `Datadog` | User-facing SDK warnings and info (init, feature flags) |
| `DD_LOG` | Internal debug: queue state, upload decisions, errors |
| `Curl` | Full outgoing HTTP requests including JSON body |

## Event Structure in Curl Logs

Each `Curl` log line is a single `curl` command. The `-d` argument contains the request body:

**Logs** (`/api/v2/logs`):
```json
[
  {
    "message": "onStart",
    "status": "debug",
    "service": "com.datadog.android.sample",
    "date": "...",
    "application_id": "...",
    "session_id": "...",
    "view.id": "...",
    "usr": { "id": "...", "anonymous_id": "..." },
    "ddtags": "build_type:debug,flavor:us1,env:debug,..."
  }
]
```

**Session Replay** (`/api/v2/replay`): `multipart/form-data` with WebP image parts — body will be binary.

**RUM** (`/api/v2/rum`): newline-delimited JSON events.

**Traces** (`/api/v2/traces`): `application/msgpack` — binary, not readable as text.

## Key Fields

| What to check | Field path |
|---|---|
| Event source | URL path in the `curl` command |
| Log message | `message` |
| RUM app ID | `application_id` |
| Session | `session_id` |
| View | `view.id` |
| User | `usr.id`, `usr.anonymous_id` |
| Tags | `ddtags` |
| Build variant | `ddtags` contains `flavor:us1,build_type:debug` |

## Common Mistakes

| Mistake | Fix |
|---|---|
| No `Curl` logs appear | `CurlInterceptor` only runs when `BuildConfig.DEBUG = true`; confirm you're on a debug build |
| Curl logs show but body is `''` | `printBody = false` (default) — change to `CurlInterceptor(printBody = true)` and rebuild |
| `rumApplicationId` is empty in payload | `config/us1.json` was created after last build — rebuild + reinstall |
| Activity class does not exist error | Wrong activity name; run `dumpsys package` to find the correct one |
| Logcat shows no events after launch | Wait ~15 seconds for the first upload cycle; SDK batches before sending |
| `401` errors on feature flags | Expected with fake credentials — logs/RUM/traces still emit and batch locally |
