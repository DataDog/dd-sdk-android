# Flags SDK Emulator Diagnostics

## What This Does

The sample app runs network diagnostics on startup to verify the Datadog feature flags endpoints are reachable from the device/emulator. It checks:

1. **Configured site** — logs which `DatadogSite` is active and the intake endpoint URL
2. **Flags CDN host** — computes the exact CDN hostname the SDK will call
3. **DNS resolution** — resolves both the flags CDN host and the intake host, logs IP addresses or failure
4. **HTTP reachability (Flags CDN HEAD)** — quick TCP/TLS probe, no auth required
5. **HTTP reachability (Exposures intake HEAD)** — quick TCP/TLS probe
6. **POST probe (Flags CDN)** — fires a properly-shaped `precompute-assignments` request with your token, logs HTTP status code and response body
7. **Flag snapshot** — once the SDK client reaches `Ready` state, logs all loaded flag keys, types, and variants

The SDK (`PrecomputedAssignmentsDownloader`) also logs the HTTP status code and response body snippet directly to logcat on failure, so you do not need to parse the raw request yourself.

All results logged to logcat under the `FlagsDiagnostics` tag. Runs on a background IO thread — no ANR risk.

## Prerequisites

- **JDK 17** — `brew install openjdk@17` (or use Android Studio's bundled JDK)
- **Android Studio** (Ladybug or newer) with:
  - Android SDK Platform 36 (API 36)
  - Android SDK Build-Tools 36.0.0
  - Android Emulator + a system image (e.g. API 34 x86_64)
- **CMake** — install via Android Studio SDK Manager > SDK Tools > CMake
- **NDK** — install via Android Studio SDK Manager > SDK Tools > NDK (Side by side)

## 1. Clone the branch

```bash
git clone -b typo/flags-emulator-diagnostics \
  git@github.com:DataDog/dd-sdk-android.git
cd dd-sdk-android
```

## 2. Create config file

The build reads credentials from `config/dd_flags.json`. This file is gitignored — you must create it manually.

```bash
mkdir -p config
cat > config/dd_flags.json << 'EOF'
{
  "site": "us1",
  "token": "<YOUR_DD_CLIENT_TOKEN>",
  "rumApplicationId": ""
}
EOF
```

| Field | Description |
|-------|-------------|
| `site` | Datadog site name: `us1`, `us3`, `us5`, `eu1`, `ap1`, `ap2`, `uk1`, `staging` |
| `token` | Client token from your Datadog org (Organization Settings > Client Tokens) |
| `rumApplicationId` | Optional — leave empty if you do not have a RUM app configured |

The `token` field determines whether the POST probe and the SDK's actual flag fetch succeed. DNS and HEAD checks work without a valid token.

## 3. Create and start an emulator

```bash
# List available system images
sdkmanager --list | grep system-images

# Install one (example)
sdkmanager "system-images;android-34;google_apis;x86_64"

# Create AVD
avdmanager create avd -n flags-test -k "system-images;android-34;google_apis;x86_64"

# Start emulator (pass -dns-server 8.8.8.8 if DNS resolution fails)
emulator -avd flags-test -dns-server 8.8.8.8 &
```

Or create one through Android Studio > Device Manager.

## 4. Build and install

The `dd_flags.json` config overrides the flavor's site and credentials — use any flavor.

```bash
./gradlew :sample:kotlin:assembleUs1Debug
adb install sample/kotlin/build/outputs/apk/us1/debug/kotlin-us1-debug.apk
```

Or open the project in Android Studio, select the `us1Debug` build variant, and hit Run.

## 5. Launch and read diagnostics

```bash
# Start the app
adb shell am start -n com.datadog.android.sample/com.datadog.android.sample.NavActivity

# Watch diagnostics output (Ctrl-C when done)
adb logcat -s FlagsDiagnostics,Datadog
```

## Interpreting Results

### Full success

```
FlagsDiagnostics: ========== FLAGS SDK NETWORK DIAGNOSTICS START ==========
FlagsDiagnostics: Configured site: US1
FlagsDiagnostics: Intake endpoint: https://browser-intake-datadoghq.com
FlagsDiagnostics: Flags CDN host: preview.ff-cdn.datadoghq.com
FlagsDiagnostics: DNS [Flags CDN] preview.ff-cdn.datadoghq.com -> 167.82.50.49
FlagsDiagnostics: DNS [Intake] browser-intake-datadoghq.com -> 3.233.x.x, ...
FlagsDiagnostics: HTTP [Flags CDN] HEAD https://preview.ff-cdn.datadoghq.com/precompute-assignments -> 405 ()
FlagsDiagnostics: HTTP [Exposures intake] HEAD https://browser-intake-datadoghq.com/api/v2/exposures -> 403 (Forbidden)
FlagsDiagnostics: POST [Flags CDN] https://preview.ff-cdn.datadoghq.com/precompute-assignments -> HTTP 200
FlagsDiagnostics: POST [Flags CDN] body: {"data":{"id":"...","type":"precomputed-assignments",...
FlagsDiagnostics: ========== FLAGS SDK NETWORK DIAGNOSTICS END ==========
FlagsDiagnostics: FlagsClient state: Ready
FlagsDiagnostics: Flag snapshot: 226 flag(s) loaded
FlagsDiagnostics:   flag: my-feature | type=boolean variant=true reason=STATIC
```

HEAD 405 (CDN) and HEAD 403 (intake) are expected — no auth on HEAD. POST 200 confirms auth + CDN routing work. `Flag snapshot` shows loaded flags.

### Invalid / revoked token

```
Datadog: Flag fetch failed with HTTP 401. Response: {"errors":[{"title":"Unauthorized","detail":""}]}
FlagsDiagnostics: FlagsClient state: Error
FlagsDiagnostics: Flag snapshot: client entered Error state: Unable to fetch feature flags...
```

Network is reachable. Token is invalid. Generate a new client token in Organization Settings > Client Tokens and update `config/dd_flags.json`.

### DNS failure

```
FlagsDiagnostics: DNS [Flags CDN] preview.ff-cdn.datadoghq.com -> FAILED: UnknownHostException: Unable to resolve host
FlagsDiagnostics: HTTP [Flags CDN] HEAD https://... -> FAILED: UnknownHostException: ...
```

Restart the emulator with `-dns-server 8.8.8.8`. If on a real device, check network/VPN/proxy settings.

## Additional SDK Logs

```bash
adb logcat -s FlagsDiagnostics,Datadog
```

`Datadog.setVerbosity(Log.VERBOSE)` is set in the sample app — all SDK-internal logs appear under the `Datadog` tag, including the verbose HTTP error details added to `PrecomputedAssignmentsDownloader`.
