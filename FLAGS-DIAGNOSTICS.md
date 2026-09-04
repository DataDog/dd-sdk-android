# Flags SDK Emulator Diagnostics

## What This Does

The sample app runs network diagnostics on startup to verify the Datadog feature flags endpoints are reachable from the device/emulator. It checks:

1. **Configured site** — logs which `DatadogSite` is active and the intake endpoint URL
2. **Flags CDN host** — computes the exact CDN hostname the SDK will call (e.g. `preview.ff-cdn.us5.datadoghq.com` for US5)
3. **DNS resolution** — resolves both the flags CDN host and the intake host, logs IP addresses or failure
4. **HTTP reachability (Flags CDN)** — `HEAD https://preview.ff-cdn.us5.datadoghq.com/precompute-assignments`
5. **HTTP reachability (Exposures intake)** — `HEAD https://browser-intake-us5-datadoghq.com/api/v2/exposures`

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

The build reads credentials from `config/dd_flags.json`. This file is gitignored.

```bash
mkdir -p config
cat > config/dd_flags.json << 'EOF'
{
  "site": "us5",
  "token": "<YOUR_DD_CLIENT_TOKEN>",
  "rumApplicationId": "<YOUR_RUM_APP_ID>"
}
EOF
```

| Field | Description |
|-------|-------------|
| `site` | Datadog site name: `us1`, `us3`, `us5`, `eu1`, `ap1`, `ap2`, `uk1`, `staging` |
| `token` | Client token from your Datadog org |
| `rumApplicationId` | RUM application ID from your Datadog org |

Get `token` and `rumApplicationId` from your Datadog org (UX Monitoring > Setup).

**To test endpoint reachability without sending real data**, leave `token` and `rumApplicationId` as empty strings — the diagnostics still run DNS + HTTP checks.

## 3. Create and start an emulator

```bash
# List available system images
sdkmanager --list | grep system-images

# Install one (example)
sdkmanager "system-images;android-34;google_apis;x86_64"

# Create AVD
avdmanager create avd -n flags-test -k "system-images;android-34;google_apis;x86_64"

# Start emulator
emulator -avd flags-test &
```

Or create one through Android Studio > Device Manager.

## 4. Build and install

Any flavor works — the `dd_flags.json` config overrides the flavor's defaults (including site).

```bash
./gradlew :sample:kotlin:assembleUs5Debug
adb install sample/kotlin/build/outputs/apk/us5/debug/kotlin-us5-debug.apk
```

Or open the project in Android Studio, select the `us5Debug` build variant, and hit Run.

## 5. Launch and read diagnostics

```bash
# Start the app
adb shell am start -n com.datadog.android.sample/com.datadog.android.sample.NavActivity

# Watch diagnostics output
adb logcat -s FlagsDiagnostics
```

## Expected Output

### Working network

```
FlagsDiagnostics: ========== FLAGS SDK DIAGNOSTICS START ==========
FlagsDiagnostics: Configured site: US5
FlagsDiagnostics: Intake endpoint: https://browser-intake-us5-datadoghq.com
FlagsDiagnostics: Flags CDN host: preview.ff-cdn.us5.datadoghq.com
FlagsDiagnostics: DNS [Flags CDN] preview.ff-cdn.us5.datadoghq.com -> x.x.x.x, ...
FlagsDiagnostics: DNS [Intake] browser-intake-us5-datadoghq.com -> x.x.x.x, ...
FlagsDiagnostics: HTTP [Flags CDN] HEAD https://preview.ff-cdn.us5.datadoghq.com/precompute-assignments -> 405 ()
FlagsDiagnostics: HTTP [Exposures intake] HEAD https://browser-intake-us5-datadoghq.com/api/v2/exposures -> 403 (Forbidden)
FlagsDiagnostics: ========== FLAGS SDK DIAGNOSTICS END ==========
```

A 405 from the CDN and 403 from the intake are expected — they confirm the server is reachable. The SDK uses POST with auth headers for real requests.

### Broken network / DNS failure

```
FlagsDiagnostics: DNS [Flags CDN] preview.ff-cdn.us5.datadoghq.com -> FAILED: UnknownHostException: Unable to resolve host
FlagsDiagnostics: HTTP [Flags CDN] HEAD https://... -> FAILED: UnknownHostException: ...
```

## Additional SDK Logs

For full SDK logging alongside diagnostics:

```bash
adb logcat -s FlagsDiagnostics -s Datadog -s "[Datadog Flags]"
```

The sample app already sets `Datadog.setVerbosity(Log.VERBOSE)`, so all SDK-internal logs appear under the `Datadog` tag.
