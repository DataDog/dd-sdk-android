# Diagnose flags startup from Logcat

Use this branch in a non-minified diagnostic build of your existing app. It adds startup timing and state-provenance observations without changing the SDK's timeouts, scheduling, cache eligibility, encryption or tracking settings. Output goes directly to Logcat, outside Datadog's persistence/upload queues. No capture script is required.

## 1. Build this branch for your app

In the SDK checkout containing these changes, use JDK 21 and the Android SDK/build tools required by its Gradle wrapper. Build the three SDK artifacts into a local Maven directory:

```sh
./gradlew -Pdd-skip-signing \
  -Dmaven.repo.local="$PWD/build/flags-diagnostic-maven" \
  :dd-sdk-android-internal:publishReleasePublicationToMavenLocal \
  :dd-sdk-android-core:publishReleasePublicationToMavenLocal \
  :features:dd-sdk-android-flags:publishReleasePublicationToMavenLocal
```

Despite the task name, this writes only to the selected local directory; it does not upload artifacts. Building the SDK separately also avoids adding its build or Gradle plugins to your app.

In the app's `settings.gradle.kts`, add that directory before the normal artifact repositories. Replace the example path with the absolute path on your machine:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("/absolute/path/to/dd-sdk-android/build/flags-diagnostic-maven")
            content {
                includeModule("com.datadoghq", "dd-sdk-android-core")
                includeModule("com.datadoghq", "dd-sdk-android-internal")
                includeModule("com.datadoghq", "dd-sdk-android-flags")
            }
        }
        google()
        mavenCentral()
    }
}
```

In the app module, replace the core/flags versions with this branch's version and add the internal module needed by the app-local diagnostic adapter:

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-core:3.16.0-SNAPSHOT")
    implementation("com.datadoghq:dd-sdk-android-flags:3.16.0-SNAPSHOT")
    implementation("com.datadoghq:dd-sdk-android-internal:3.16.0-SNAPSHOT")
}
```

Merge these entries into your existing configuration. Keep your other SDK configuration, feature settings, client identity and evaluation context unchanged. Rebuild with `--refresh-dependencies` after rebuilding artifacts under the same snapshot version. Verify the resolved versions, not only the declarations:

```sh
./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency dd-sdk-android
./gradlew :app:assembleDebug --refresh-dependencies
```

Use your actual module and variant names if different. Confirm core, internal and flags all resolve to the local `3.16.0-SNAPSHOT` artifacts. This branch is based on develop `39ccdf81843e5ab2089575200707b6045e3e89c4`; it includes later CACHED/first-flags behaviour and is not an exact reproduction of a released 3.14.0 binary combination.

## 2. Register the diagnostic adapter

Copy `src/main/kotlin/com/datadog/android/diagnostics/FlagsStartupCapture.kt` from this directory into the app's `src/main/kotlin/com/datadog/android/diagnostics/` directory. It remains an app-local class, not a new SDK API.

Call it before your existing Datadog initialization, so executor and cache submission events are captured:

```kotlin
import com.datadog.android.diagnostics.FlagsStartupCapture

// In Application.onCreate(), before Datadog.initialize(...):
FlagsStartupCapture.installIfEnabled(BuildConfig.DEBUG)

// Continue with your existing Datadog, Flags and client initialization.
```

Use the app's `BuildConfig.DEBUG`. Keeping the file in `src/main` lets both app variants compile; this call enables registration only in a debug build. Do not rename `FlagsStartupCapture` or minify the diagnostic build: the SDK helpers recognise that explicit adapter name. Do not add extra resolutions, listeners or a flag-enumeration loop just to produce a trace.

## 3. Enable and collect Logcat

Install the diagnostic app with `adb install -r` or Android Studio's normal update installation, retaining its data. Set your application ID below, enable the tag, then restart the app:

```sh
APP_ID=com.example.yourapp
adb shell setprop log.tag.DDFlagsStartup DEBUG
adb shell am force-stop "$APP_ID"
adb shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1
adb logcat --pid="$(adb shell pidof -s "$APP_ID")" -v raw 'DDFlagsStartup:I' '*:S' > flags-startup.log
```

Stop Logcat with Ctrl-C after startup. It includes buffered records for that process, so launching immediately before collection retains the early initialization events. Do not clear app data or Logcat between runs.

Capture stops after 60 seconds or 20,000 records, whichever is reached first. Expiry is checked on the next diagnostic observation; there is no timer or extra thread. `capture_stopped` means later work and missing span endings are outside the capture. Each app restart creates a fresh capture while the tag remains enabled.

To disable diagnostics, change the tag and restart the app:

```sh
adb shell setprop log.tag.DDFlagsStartup WARN
adb shell am force-stop "$APP_ID"
adb shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1
```

Changing the tag alone does not cancel a capture already registered in the running process. After the investigation, remove the adapter/registration and restore the app's normal dependencies and repository configuration.

## 4. Compare startup conditions

First run online and verify a network installation followed by `cache.write_result` with `success=true`. This establishes that a cache save completed; an installed network state alone does not prove persistence. Then retain app data and use the same build, SDK/client identity, context and tracking settings for:

- A warm-cache restart without connectivity.
- A warm-cache restart online.
- If a controlled assignment proxy is available, an online restart with its response deliberately delayed. Apply delay outside the SDK; do not change SDK timeouts or waits.

Keep one Logcat file per run and record the network condition. Do not change encryption or endpoint settings between the initial comparisons.

## 5. What to look for

Records contain monotonic `ns`, process/thread IDs, span `id`, synchronous `parent`, and diagnostic properties. Pair `begin`/`end` with the same PID and span ID. Duration in milliseconds is `(end.ns - begin.ns) / 1_000_000`. Request/operation IDs link asynchronous submission to execution; repository/state IDs link installations to reads. State IDs identify objects, not installation order.

| Question | Logcat evidence |
| --- | --- |
| Did the cache wait in a queue? | Compare `cache.enqueue` and `cache.execute` for the same `request`. For named tasks, pair `executor.enqueue` and `executor.start` using `executor` and `task`. |
| What ran ahead of it? | Look at `executor.start` spans on that storage executor during the queue interval. Names such as `ntp_sync_initialization`, `ndk_crash_check`, `data_migration` and `datastoreread` identify actual tasks. A submission without a start does not prove acceptance: backpressure, rejection or the capture boundary may explain it. |
| Was reading or parsing slow? | `cache.file_tlv_read` covers the configured file/TLV reader, including encryption handling; `cache.decode` covers byte-to-string conversion and reports byte count; `cache.json_parse` is separate. `cache.parse_result` reports validity and flag count. |
| Was the cache installed? | `state.cache_cas` brackets the existing CAS; `state.install` reports `source=disk`, candidate `state`, and `accepted=true/false`. `cache.missing` / `cache.read_failure` / invalid parse distinguish unsuccessful paths. A valid empty configuration is still a successful installation. |
| Did network replace it? | `state.network_swap` brackets the swap. `state.install` with `source=network` includes `previous_state` taken from that swap's actual return value. |
| Why did resolve wait? | `latch.wait` identifies the caller; nested `latch.result` reports `completed`, `timeout` or `interrupted`. `latch.release` labels `disk_callback` or `network_installation`. Overlapping release scopes do not prove which one won. |
| Which state did resolve use? | Within `resolution.lookup`, use `state.read` with `caller=getPrecomputedFlagWithContext`. It describes the exact state used by that lookup. The preceding readiness check, `getEvaluationContext`, may read a different state. `state=0, source=none` means no state. |
| Was it a fallback? | `resolution.result` contains success and reason, or fallback and error code. |
| Where was network time spent? | `network.request`, `network.submit`, `network.execute`, `network.http`, `network.body` and `network.map` separate scheduling, HTTP, body reading and mapping. `network.status` / `network.result` report outcome. |
| Were callbacks or tracking slow? | Inspect `lifecycle.notify`, `network.notify`, `first_flags.notify` and `resolution.tracking`. |

A roughly 100 ms latch wait does **not** establish that disk reading took 100 ms. The trace separates queueing, file reading, parsing and the wait itself. Logcat formatting adds observer overhead, so compare phases and provenance rather than treating these durations as benchmark measurements.

The diagnostic tag includes IDs, timings, counts and outcomes, not flag keys/values, evaluation contexts, request bodies, tokens or exception messages. Other app/SDK Logcat tags are outside this capture.
