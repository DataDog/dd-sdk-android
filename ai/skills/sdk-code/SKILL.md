---
name: sdk-code
description: >-
  Coding conventions and architecture for dd-sdk-android. Use when implementing,
  modifying, debugging, refactoring, or reviewing production SDK code.
when_to_use: >-
  Adding SDK features or integrations, fixing implementation bugs, changing public
  APIs, or reviewing threading, logging, compatibility, and module placement.
---

# dd-sdk-android Coding Context

## Mandatory rules

- **NEVER commit on your own.** Only commit when the user explicitly asks for it.
- **NEVER edit `detekt.yml`.** This is the shared lint config — changing a rule's `active` flag to silence a warning hides the underlying issue instead of fixing it. Fix the code, not the linter.
- **ALWAYS write tests** for any code you create or modify. Load the sibling `sdk-tests` skill (via the `Skill` tool) whenever you write or touch test files, and follow its conventions exactly.
- **PAY attention to review concerns.** Before making any design or implementation decision, check whether the sibling [sdk-revirew](../sdk-revirew/SKILL.md) skill or the current PR review has raised a concern about that area. Prefer the reviewer's guidance over your own default.

---

## Build commands

The root `build.gradle.kts` registers aggregate tasks that run across all submodules:

```bash
./gradlew assembleLibrariesDebug       # build all library modules (debug)
./gradlew assembleLibraries            # build all library modules (debug + release)
./gradlew unitTestDebug                # unit tests, all modules, debug variant
./gradlew unitTestRelease              # unit tests, all modules, release variant
./gradlew lintCheckAll                 # lint all modules
./gradlew checkAll                     # lint + unit tests + instrumented tests
./gradlew instrumentTestAll            # all instrumented (integration) tests — requires connected device/emulator
```

Run a single module's tests:

```bash
./gradlew :features:dd-sdk-android-rum:testDebugUnitTest
./gradlew :features:dd-sdk-android-rum:testDebugUnitTest --tests "com.datadog.android.rum.internal.monitor.DatadogRumMonitorTest"
```

Sample app requires a flavor name:

```bash
./gradlew :sample:kotlin:assembleUs1Debug   # us1 is the default flavor
```

Code formatting (requires ktlint installed):

```bash
ktlint -F "**/*.kt" "**/*.kts" '!**/build/generated/**' '!**/build/kspCaches/**'
```

### API surface files

Every publishable module has `api/apiSurface` and `api/<module>.api` checked into git. CI fails if these are stale. After any public API change, regenerate them:

```bash
./gradlew :features:dd-sdk-android-rum:generateApiSurface   # updates api/apiSurface
./gradlew :features:dd-sdk-android-rum:apiDump              # updates api/<module>.api
./gradlew checkApiSurfaceChangesAll                         # verify all modules (CI check)
./gradlew checkTransitiveDependenciesListAll                 # verify transitive deps lists
./gradlew checkCompilerMetadataChangesAll                   # verify compiler metadata
```

### Integration tests

Integration tests live under `instrumented/integration` and `reliability/` and run on a connected device or emulator:

```bash
./gradlew instrumentTestAll                                        # all integration tests
./gradlew :instrumented:integration:connectedDebugAndroidTest     # legacy integration suite
./gradlew :reliability:core-it:connectedDebugAndroidTest          # core integration tests
```

Individual reliability modules (each requires a connected device):

```bash
./gradlew :reliability:single-fit:logs:connectedDebugAndroidTest
./gradlew :reliability:single-fit:rum:connectedDebugAndroidTest
./gradlew :reliability:single-fit:trace:connectedDebugAndroidTest
./gradlew :reliability:single-fit:okhttp:connectedDebugAndroidTest
```

---

## Architecture

### Module layers

| Module | Responsibility |
|--------|---------------|
| `dd-sdk-android-core` | Storage, upload, `DatadogCore` (`InternalSdkCore`), persistence, networking |
| `dd-sdk-android-internal` | Shared internal APIs, utilities, `@NoOpImplementation` annotation |
| `features/dd-sdk-android-rum` | RUM: user sessions, views, actions, resources, errors |
| `features/dd-sdk-android-logs` | Logs feature |
| `features/dd-sdk-android-trace` | Tracing (spans) |
| `features/dd-sdk-android-session-replay` | Screen capture |
| `features/dd-sdk-android-ndk` | NDK crash reporting |
| `features/dd-sdk-android-*` | Other product features |
| `integrations/dd-sdk-android-*` | Thin bridges to OkHttp, Compose, Coil, Apollo, etc. |
| `tools/` | Detekt rules, KSP annotation processors (noopfactory), lint, unit test utils |

### Feature registration pattern

Each product is a `StorageBackedFeature` registered with the `SdkCore`. The core provides storage and upload; features implement:

- `onInitialize(appContext)` — set up monitors, strategies, writers
- `onStop()` — tear down
- `requestFactory` — serializes batched events into HTTP requests
- `storageConfiguration` — controls file size, retention, consent behaviour

Features communicate cross-module by publishing/receiving typed events via `sdkCore.sendEvent()` / `FeatureEventReceiver.onReceive()`.

### RUM scope tree

`DatadogRumMonitor` owns a `RumApplicationScope` root. All RUM events arrive as `RumRawEvent` sealed subclasses dispatched to the scope tree:

```
RumApplicationScope
  └── RumSessionScope          (one active session; manages session sampling, timeseries)
        └── RumViewManagerScope
              └── RumViewScope           (one per screen)
                    ├── RumActionScope
                    ├── RumResourceScope
                    └── (errors, long tasks written directly from view scope)
```

Each scope's `handleEvent()` returns itself while valid, or `null` when it should be dropped. Scopes write serialized events via `DataWriter<Any>`, which routes through `ConsentAwareStorage` to encrypted files on disk.

### NoOp code generation

Interfaces annotated with `@NoOpImplementation` (from `tools/noopfactory`, KSP) get a generated `NoOp<InterfaceName>` class at build time. This is how the SDK avoids null checks everywhere — uninitialized features use NoOp stubs. When you add a new interface that needs a NoOp, annotate it and rebuild.
