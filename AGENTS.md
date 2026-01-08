# AGENTS.md

Datadog SDK for Android - Kotlin/Java observability (Logs, Traces, RUM, Session Replay, NDK, WebView, Flags).

- Philosophy: [ZEN.md](ZEN.md)
- Setup, code style, testing conventions, CI commands: [CONTRIBUTING.md](CONTRIBUTING.md)

## File Header

All `.kt` and `.kts` files must start with:

```kotlin
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
```

## Commit & PR Conventions

- Branch: `<username>/<JIRA-ID>/<short-description>` (internal) or `<username>/<short-description>` (external)
- Commit: `<JIRA-ID>: <description>` (internal) or `<description>` (external)
- PR title: same as commit format
- PR body: What, Motivation, Additional Notes (see [PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md))
- Before PR: run `./local_ci.sh -c -n -a` (compile, clean, analysis), then `./local_ci.sh -t` (tests)

## Commands

```bash
# Test single module
./gradlew :dd-sdk-android-core:testDebugUnitTest
./gradlew :features:dd-sdk-android-rum:testDebugUnitTest
./gradlew :integrations:dd-sdk-android-okhttp:testDebugUnitTest
./gradlew :module:testDebugUnitTest --tests "*.SomeTest"   # single test
```

## Structure

```
dd-sdk-android-core/         # core: init, storage, upload
dd-sdk-android-internal/     # shared internal APIs
features/                    # logs, rum, trace, ndk, session-replay, webview, flags
integrations/                # okhttp, timber, coil, glide, compose, rx, etc.
reliability/                 # integration tests
tools/                       # detekt, lint, unit utils
```

## Architecture

- `DatadogCore` → `CoreFeature` → features
- Init: `Datadog.initialize()` then `Logs.enable()`, `Rum.enable()`, etc.
- Patterns: Registry, Provider (`TimeProvider`), Scope (RUM), DataWriter/Reader
- Forgeries: `{module}/src/test/.../forge/FooForgeryFactory`

## Constraints

- Min SDK 21, Java-compatible APIs, zero crashes, signed commits
