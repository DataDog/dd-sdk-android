# Flags SDK Emulator Diagnostics

## What This Does

The sample app runs network diagnostics on startup to verify the Datadog feature flags endpoints are reachable from the device/emulator. It checks:

1. **Configured site** — logs which `DatadogSite` is active and the intake endpoint URL
2. **Flags CDN host** — computes the exact CDN hostname the SDK will call
3. **DNS resolution** — resolves both the flags CDN host and the intake host, logs IP addresses or failure
4. **HTTP reachability (Flags CDN HEAD)** — credential-free TCP/TLS probe with presented certificate chain, platform trust result, proxy/route, and negotiated TLS details
5. **HTTP reachability (Exposures intake HEAD)** — the same TLS diagnostics for intake
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

## TLS / certificate failures

Startup also logs Android version/API/security patch, model/hardware, app target SDK and
debuggable state, Network Security Configuration resource, device clock, and installed
system/user CA counts. Installed CA counts do **not** imply that the app trusts those CAs.
Apps targeting API 24+ normally do not trust user-installed CAs unless explicitly configured.

Each HEAD probe uses a fresh connection and a recording trust manager which logs the
**server-presented chain before validation**, including on rejected handshakes:

- Subject and issuer, serial number, CA basic constraint, signature algorithm.
- Validity dates, certificate SHA-256 fingerprint, public-key/SPKI SHA-256 fingerprint
  (colon-separated hex, not the base64 `sha256/...` format used by OkHttp pins).
- Subject alternative names (SANs).
- Android's hostname-aware platform trust result and full failure stack/cause chain.
- On trust success, the validated chain's final certificate subject and fingerprint.
- Selected proxy and actual connection address; negotiated TLS version/cipher on success.

These probes still enforce Android's hostname-specific trust policy and OkHttp's hostname
verification. They do not install a CA, trust all certificates, disable hostname verification,
or change the SDK/authenticated POST client. Redirects and connection retries are disabled
for these HEAD probes; each call has a 20-second timeout. A separate client/connection is
used, so its observed route/chain is diagnostic evidence, not a capture of the SDK's own
connection. A successful platform trust check alone is not proof that hostname verification
or any later pin check passed; the final HEAD result determines overall probe success.

### Collecting a complete run

Start log capture **before** launching the app, then retain output through the diagnostics
END marker (both HEAD probes may take up to 20 seconds each):

```bash
adb logcat -v threadtime -s FlagsDiagnostics Datadog > flags-diagnostics.log
# In another terminal, launch/restart the sample app.
```

Review logs before sharing: certificate subjects/SANs and proxy addresses may identify
internal infrastructure. Existing POST/flag snapshot diagnostics can contain response data
and flag names. The new TLS logger does not log request headers or client tokens.

### Interpreting certificate evidence

- **Corporate/security-product issuer + trust-anchor failure:** supports HTTPS inspection
  using a CA the Android app does not trust. Compare with an IT-approved inspection exemption.
- **Public CA + trust-anchor failure:** compare emulator CA store/API level, app trust config,
  and supplied intermediates with the working device. Missing intermediates can also cause this.
- **No presented-chain lines:** failure may precede certificate validation (DNS, TCP, proxy,
  protocol negotiation); inspect the HEAD exception and route logs.
- **DIRECT proxy:** does not exclude a transparent proxy, host VPN, or endpoint security agent.
- **Different leaf fingerprints:** not sufficient proof of interception; CDNs can use multiple
  valid certificates. Compare issuers and chains too.
- **Root missing from presented chain:** normal; servers generally send the leaf and intermediates,
  while the trust anchor comes from the client. The presented list is not the validated path.

Compare the same APK on emulator and real device, noting their network paths. Do not fix a
trust-anchor failure by disabling validation. If inspection is intentional, use an approved
CA via debug-only configuration or an IT-managed inspection exemption.

## Inspecting, clearing, or refreshing certificate stores

Collect the failing diagnostics **before** changing the emulator, so the original chain and
CA inventory remain available for comparison.

1. **Inspect Android trust:** in the emulator's Settings, search for `Trusted credentials`
   or `Encryption & credentials`. On recent Pixel-style images the path is typically
   Security & privacy > More security settings > Encryption & credentials > Trusted credentials.
   Inspect both System and User tabs; open a CA to compare its subject/fingerprint with the
   diagnostic chain. Labels vary by system image. A leaf's issuer may be an intermediate,
   so it need not directly match a root's subject.
2. **Remove a user-installed certificate:** use the User tab's certificate details or
   User credentials, depending on Android version. `Clear credentials` removes user-installed
   credentials, not permanent system roots. It can disrupt certificate-based Wi-Fi/VPN access;
   remove only a known test CA, or use a separate AVD instead. Clearing app storage or reinstalling
   the APK does not reset Android's CA store.
3. **Reset a disposable AVD:** stop it, then Android Studio > Device Manager > its menu >
   Wipe Data. This deletes installed apps, settings, and user credentials and returns user data
   to the defaults of the existing image. It does not download newer system roots. Cold Boot
   alone does not clear certificates either.
4. **Refresh the system-image baseline:** use SDK Manager to download/update the desired Android
   system image and create a new AVD with it. Install the same APK and compare. This preserves
   the failing AVD and avoids carrying over old snapshots or manually modified trust settings.
   There is no universal certificate-store refresh button across Android versions/images.
5. **Replace a corporate CA:** get the current CA and verified fingerprint from the customer's IT
   team. Install it using Encryption & credentials > Install a certificate > CA certificate,
   if their policy requires it. Installing a user CA alone is insufficient for apps targeting
   API 24+ unless their trust configuration permits it. Restart the app after trust changes.

The workstation OS trust store, Android Studio's JDK trust store, and emulator/app trust are
separate. Updating the workstation or Java CA store does not automatically fix Android app
trust. If a host security agent keeps substituting a corporate certificate, resetting the
emulator will not stop that interception; use the approved CA policy or an inspection exemption.

References:
- [Android certificate settings](https://support.google.com/pixelphone/answer/2844832?hl=en)
- [Android app trust configuration](https://developer.android.com/privacy-and-security/security-config)
- [Create/manage AVDs and wipe data](https://developer.android.com/studio/run/managing-avds)

## Evidence package for the customer's Zscaler team

Capture a complete run from START to END using `adb logcat -v threadtime -s FlagsDiagnostics Datadog`.
The `EVIDENCE` and `HANDOFF` lines summarize each HEAD probe. Attach the surrounding `TLS`
certificate lines, `Route`/`Proxy` lines, Android/app configuration, clock, and exception causes.
The hostname-aware callback logs certificates before Android can reject the chain.

Suggested ticket content:

> On the attached timestamp, the Android emulator attempted HTTPS to the hostname shown in
> the EVIDENCE line on port 443. The attached certificate subjects, issuers, and SHA-256
> fingerprints describe the chain received by this emulator probe. Android's validation
> result and exception are included. Please correlate the hostname and timestamp with the
> applicable Zscaler SSL-inspection policy and confirm the signing CA and complete chain.
> If inspection is intended, please confirm the supported CA provisioning and application
> trust configuration for Android. Alternatively, test an approved no-decrypt exception for
> this exact hostname and compare the certificate chain and HEAD outcome before/after.

Include separately the customer's workstation/user or Zscaler device identifier using their
normal IT process; the diagnostics deliberately do not collect these identifiers. Attach the
workstation OpenSSL capture as a separate observation, not as the emulator's chain.

`zscalerNamedInPresentedChain=true` means a certificate subject or issuer contains Zscaler;
it is evidence consistent with inspection, not cryptographic authentication of an untrusted CA.
`false` does not rule it out (custom enterprise CAs may have other names). CA counts do not
prove the required CA is trusted by this app. `ACCEPTED` describes certificate-path validation;
check the final HEAD outcome for hostname/pin/HTTP results. HEAD 403/405 can still demonstrate
successful TLS. Do not send client tokens or flag-response data in an IT ticket; review/redact
existing POST and snapshot logs before sharing.
