# Datadog Feature Flags SDK for Android

The Datadog Feature Flags SDK for Android allows you to evaluate feature flags and experiments in your Android application and automatically send flag evaluation data to Datadog for monitoring and analysis.

## Getting started

Add the Datadog Feature Flags SDK to your application's `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-flags:<latest-version>")
    
    // Recommended: RUM integration drives analysis and enriches RUM session data
    implementation("com.datadoghq:dd-sdk-android-rum:<latest-version>")
}
```

### Initial setup

Before enabling the Feature Flags feature, you must first initialize the Datadog SDK. See the [Datadog Android SDK setup documentation][1] for details.

```kotlin
// Initialize the core Datadog SDK first
val coreConfiguration = Configuration.Builder(
    clientToken = "<YOUR_CLIENT_TOKEN>",
    env = "<YOUR_ENVIRONMENT>",
    variant = "<YOUR_APP_VARIANT>"
)
    .build()

Datadog.initialize(this, coreConfiguration, trackingConsent)
```

### (Recommended) Enable RUM in your application

RUM session data is enriched with Feature Flag evaluations and is used to drive the analysis for your Feature Flags. Skip this step if RUM is already configured
or you are opting out of the RUM integration.

```kotlin
val rumConfig = RumConfiguration.Builder(applicationId = "<YOUR_RUM_APPLICATION_ID>")
    .build()
    
Rum.enable(rumConfig)
```

If RUM is not enabled, the Flags SDK works normally but flag evaluations will not appear in RUM views.

## Setup

### Enable the Feature Flags Feature

After initializing the Datadog SDK, enable the Feature Flags feature:

```kotlin
val flagsConfig = FlagsConfiguration.Builder().build()
Flags.enable(flagsConfig)
```

### Configuration options

The `FlagsConfiguration.Builder` supports the following options:

#### Response signing (experimental)

Response signing is experimental. `ResponseSigningMode` and
`FlagsConfiguration.Builder.experimentalResponseSigning()` require `@OptIn(ExperimentalFlagsApi::class)`.
The API can change or be removed in a minor release, based on customer feedback.

The SDK does not send `X-stream`. Signed and unsigned requests keep their ordinary transport selection.
The edge buffers the responses that it signs, because a v1 proof covers the whole body.

The first public response-signing protocol is v1. The SDK advertises
`response-signing-v1`, sends signature version `1`, and verifies the
`datadog.feature-flags.precomputed-assignments.v1` transcript.
X.509 v3 certificate encoding is unchanged.

A v1 response carries the leaf in `x-datadog-feature-flags-signing-certificate` and
1 to 3 intermediates in `x-datadog-feature-flags-signing-chain`. The chain value is
standard base64 DER entries joined by `,` without spaces. Index 0 issued the leaf, each
next entry issued the previous one, and a pinned root issued the last entry. The SDK
rejects a response that has only the old `x-datadog-feature-flags-signing-intermediate`
header. The transcript binds `SHA256(leaf)`, one count byte, and the SHA-256 of each
intermediate in header order. The signed response list follows the certificate hashes. The leaf extended key usage must contain the FFE
assignment-signing purpose and must not contain `anyExtendedKeyUsage`. The purpose OID
is a placeholder until Datadog assigns the production value. An intermediate at
index `i` must have a path length of at least `i`. An intermediate or root extended key
usage, if present, must contain the FFE purpose or `anyExtendedKeyUsage`. A root path
length, if present, must admit every intermediate.

Response signing is disabled by default. A client that does not opt in sends the same
requests as before and handles responses as before.

Staging only; production is not supported yet. Signing works only when the SDK targets
`DatadogSite.STAGING`. Production has no root or EKU yet, and support comes in a later release.
For every other site:

- `REQUIRED` fails closed. The client logs an error once, sends no assignment request, and fails
  every fetch with `FlagsResponseSigningException` (reason `unsupported-site`). The client state is `Error`, and evaluations
  return defaults. Remove `REQUIRED` or use `OBSERVE`.
- `OBSERVE` has no effect. The client logs a warning once and sends unchanged requests, so flags
  are not verified.

```kotlin
@OptIn(ExperimentalFlagsApi::class)
val flagsConfig = FlagsConfiguration.Builder()
    .experimentalResponseSigning(ResponseSigningMode.REQUIRED)
    .build()
```

An opted-in request adds `response-signing-v1` to `X-DD-FEATURE-FLAGS-CAPABILITIES`, sends
signature version `1`, and sends a fresh 32-character lowercase hex nonce in
`x-datadog-feature-flags-request-nonce`.

Signed header lists:

- The request sends `x-datadog-feature-flags-signed-headers`. It names each bound request header
  that the request carries: `content-type`, `dd-application-id`, `x-dd-feature-flags-capabilities`
  and `x-rkyv`. The staging-only `x-use-cache` is bound only when it is listed. The names are lowercase, sorted, unique, and joined by `,` without spaces.
- The transcript binds the list as one count byte, then the length-prefixed name, one presence byte
  and the length-prefixed value of each name. `dd-client-token` and `authorization` keep their hashed fields.
- A listed request header must be present on one line. Only the capabilities value can contain a comma.
  A bound header that is present but not listed, or a list without `content-type` and the capabilities
  header, gives the reason `unsupported-request`.
- The response must carry `x-datadog-feature-flags-signed-response-headers`, in the same syntax.
  It must name `content-type`, and `dd-flag-selection-ignored` when the request has a flag selection.
  The transcript binds this list after the certificate hashes. An absent listed header is bound as absent.
- A missing response list is `missing-proof`. Any other response list error is `invalid-proof`.
- On a verified response, the SDK acts only on the verified values of the response list.

The SDK pins one root: `CN=Datadog Customer Signing Root CA - staging`, SHA-256
`0e469f3cecc9cd51fd64373879636540512d56e99eecd70361c78b98c47ea934`. A response never
supplies the root. The SDK uses no system trust store and downloads no certificates.
Every certificate, including the root, must satisfy `notBefore <= now < notAfter`.
Each child validity must be inside its parent validity. The SDK does not check leaf issuance freshness.

The server rotates the leaf daily inside a 120-hour validity window. Each response carries
its own leaf and chain. The SDK does not pin or cache a leaf or intermediate identity, so any
valid chain under the root passes. Routine leaf and intermediate rotation needs no SDK update
or prior successful fetch. An SDK version stops verifying responses after its bundled root certificate expires.

- `OFF` preserves existing transport and persistence and performs no signing work.
- `OBSERVE` reports bounded telemetry on whether each response is verified, with the reason code, and accepts otherwise-valid
  responses when optional verification fails. Authentication, transport, redirect, and
  JSON errors remain failures. Signed and unsigned requests are never retried as a downgrade.
- `REQUIRED` accepts only valid proofs. A response with a missing or invalid proof is never applied. Before the first verified response, and after a context change, evaluations return defaults. If earlier verified flags remain eligible for the current context and request scope, a failed refresh keeps them as `Stale`; with `expireVerifiedFlags(true)`, they stop at their proof expiry.
  Verified assignments are held only in memory. Restart or a changed request scope causes defaults.
  Stopping the Flags feature immediately invalidates prior proofs and prevents in-flight
  responses from restoring them.
- RUM is not part of the signing scope. A RUM start, a RUM stop or a change of the RUM
  application ID keeps the verified flags, keeps an in-flight response, and does not change the
  state. Each response transcript still binds the `dd-application-id` that its request sent.

When verification fails, the SDK keeps the reason. In `REQUIRED` mode, the fetch fails with
`FlagsResponseSigningException`, and `reason.code` is the cross-SDK code. It is never the network
error. The SDK logs `Flag response signing failed (<code>): <what failed>. <fix hint>.` once per code,
as an error in `REQUIRED` mode and as a warning in `OBSERVE` mode.

Telemetry uses one bounded event on the core telemetry channel. Its message is
`flags.response_signing <mode> <result> [<reason>]`, so each outcome has its own telemetry identity. It
has the fields `mode` (`observe` or `required`), `result` (`verified` or `unverified`), `reason` (a code
below, only when unverified), `sdk_version` and `site`. `OBSERVE` reports every result, `REQUIRED`
reports failures only, and `OFF` reports nothing. The events need RUM: the RUM telemetry handler
samples them and deduplicates them by identity for each RUM session. Without RUM, no signing
telemetry is sent. The event never carries flag keys or values, headers, tokens, nonces, certificate
bytes, serials, URLs or the targeting context.

| Code | Meaning |
| --- | --- |
| `unsupported-request` | The SDK cannot sign this request shape. |
| `unavailable-crypto` | The platform crypto API is not available. |
| `missing-anchor` | No trusted root is configured for this site. |
| `missing-proof` | The response has no signature headers. |
| `invalid-proof` | The signature headers are present but not valid. |
| `response-too-large` | The body is larger than the signed size limit. |
| `invalid-certificate` | The certificate chain does not chain to the pinned root, or a chain rule fails. |
| `invalid-signature` | The signature does not match the request and response. |
| `expired-proof` | The proof is expired or not yet valid. |
| `unsupported-site` | Signing is not available on this Datadog site. |
| `revoked-certificate` | Draft revocation: the leaf is older than the stored minimum leaf not-before. |

When the proof of the last verified response expires in `REQUIRED` mode:

- By default (`expireVerifiedFlags(false)`), the client keeps serving the last verified flags,
  and its state changes to `FlagsClientState.Stale`. The OpenFeature provider sends
  `PROVIDER_STALE`. A network attacker can block refreshes and keep the flags at their last
  verified values. The attacker cannot forge values. The guarantee is "verified once", not "fresh".
- With `expireVerifiedFlags(true)`, the client stops serving the flags, its state changes to
  `FlagsClientState.Error`, and evaluations return defaults. This applies from `Ready` and from
  `Stale`. This gives the strict guarantee.
- The SDK detects expiry with a timer at `expires-at` and with a check on each flag read, so
  the state and its event change at the first of the two. Expiry also applies while a refresh is
  `Reconciling`; the refresh stays pending, and its verified response restores `Ready`.
- The device clock may step back at most 30 s before the time of admission (the same allowance as
  for `issued-at`). A larger step is handled as at expiry: `Stale` with the verified flags, or `Error`
  with `expired-proof` when `expireVerifiedFlags(true)`. When the clock returns into the proof window,
  the state returns to what it was before the step.
- A wall-clock step during the process does not extend a proof. The expiry clock is the later of the
  verifier clock (below) and the verification time plus the elapsed time since verification
  (`SystemClock.elapsedRealtime`). The verification time is the later of `issued-at` and the verifier
  clock at verification, so a response that arrives late in its window keeps its original deadline.
- By default, a verified response whose proof expires between verification and installation is installed,
  and the client goes directly to `Stale`, never `Ready`. With `expireVerifiedFlags(true)`, it is not installed.
- A new verified response returns the client to `FlagsClientState.Ready` in both settings.
  The customer decides when to refetch.
- `expireVerifiedFlags` has no effect in `OBSERVE` or `OFF` mode.

Clock floor (the device owner can set the clock back):

- Every time check of the proof and the chain uses `effectiveNow`, the highest of the device clock,
  the release build floor, and the issued-at high-water mark of the trust anchor. The checks are
  `issued-at <= effectiveNow + 30`, `expires-at > effectiveNow`, and each certificate is valid at
  `effectiveNow`. The reason codes do not change: an expiry gives `expired-proof`.
- The build floor is a unix time compiled into each release (`ResponseSigningVerifier.BUILD_FLOOR`).
  Each release sets it to the release date or shortly before it. A device clock set before the floor
  cannot make valid a leaf that expired before the release.
- The high-water mark is the highest `issued-at` of a fully verified response, per trust anchor, and it
  never goes down. One response raises it to at most the device clock plus 30 s, so a leaked key cannot
  move it far into the future. A device clock set back below the mark cannot make an older proof or
  leaf valid again. A device clock that is behind real time, but above the mark, behaves as before.
- The SDK stores the mark next to the minimum leaf not-before, in the same SharedPreferences. When a
  write is lost, the SDK keeps the value in memory for the process and accepts the response. A value
  that a backup restore brings back is lower, so it is safe.
  Several SDK cores share the same SharedPreferences instance, and each update locks it, so a lower value
  never overwrites a higher one.
- The 30 s backward-step allowance (above) applies to the device clock. It never lowers `effectiveNow`.

Minimum leaf not-before revocation (draft wire, pending the edge and the shared fixtures):

- A response can carry `x-datadog-feature-flags-minimum-leaf-not-before`: unix seconds, one canonical
  decimal value. When present, the SDK appends `0x01` and the value as a big-endian u64 to the
  v1 transcript, after the signed response list. When absent, the transcript is unchanged.
- The SDK accepts the value only from a fully verified response. It ignores a value that is
  lower than the stored value. It ignores a value that is greater than the `notBefore` of the
  leaf in the same response, with the reason `minimum-not-before-ignored`, because a response
  must never revoke its own leaf.
- The SDK stores the highest value per trust anchor (the key is SHA-256 of the root DER) in
  SharedPreferences, next to the issued-at high-water mark. These are the only data that `REQUIRED`
  mode persists. They hold no flag data.
- On each verification, the SDK rejects a leaf whose `notBefore` is lower than the stored value,
  with the reason `revoked-certificate`. `REQUIRED` fails closed, and `OBSERVE` accepts the
  response as unverified. Both modes store new values.
- The floor applies to leaves only. The SDK never compares it with intermediates, so a new leaf
  under older, long-lived intermediates still verifies.

```kotlin
@OptIn(ExperimentalFlagsApi::class)
val strictConfig = FlagsConfiguration.Builder()
    .experimentalResponseSigning(ResponseSigningMode.REQUIRED)
    .expireVerifiedFlags(true)
    .build()
```

Observe mode keeps one persisted entry per client, under the key `flags-…signing-v1-observe-state-<client name>`.
No client name can produce this key. The entry is tagged with a hash of the request scope: the
emitted token, endpoint and query, environment, evaluation context, and bound request headers. The RUM
application ID is not part of the scope. When a transport changes the request, the entry uses the
scope of the request that the transport sent. A client built in another signing mode removes the
entries that this mode left on disk.
The client reads the observe entry only after it forms the request, so the first opt-in is a cold start.
Old client-name-only cache entries are not imported. A valid proof also creates a separate
verified memory fallback, which unsigned observations cannot overwrite or extend.
Persisted entries are never treated as verified.

The draft verifies complete buffered JSON assignment responses over HTTPS, before parsing
or installing them. It does not introduce a streaming protocol. The shared conformance
corpus and native host-JVM tests are development evidence; Android device/provider tests,
iOS API review, production anchor provisioning, and operational rollout approval remain
release prerequisites.

The signing fixtures come from ffe-system-test-data through the shared fixture pipeline. Two sources in
`ci/feature-flags-precomputed-fixtures.json` pin `response-signing/v1/` and `response-signing/chain/`. The fetch
writes them to the git-ignored `src/test/resources/response-signing-v1/` and `src/test/resources/response-signing-chain/`
folders before unit tests and instrumented test assets are built. `SOURCE.json` in each folder records the commit and
file checksums.
The JVM and Android instrumented verifier tests discover every manifest-listed suite and check its checksum and counts.
They assert the acceptance decision for every response vector in the manifest, and the exact transcript for each
vector that is expected to pass. The JVM test also checks the reason code of each rejected vector.
A case `anchor_der_base64` replaces the file anchor for that case.
Clients do not run the `signing_cases`, because they hold no signing keys.
`rejection_reason` is a rule code; the tests do not compare native error messages.
`ResponseSigningChainTest` adds test-CA-issued chains and minted profile cases, including
nonminimal KeyUsage named bits, with the fetched test-only credentials in `src/test/resources/response-signing-chain/`.

```shell
python3 ci/sync-feature-flags-precomputed-fixtures.py
python3 ci/sync-feature-flags-precomputed-fixtures.py --verify
./gradlew :features:dd-sdk-android-flags:connectedDebugAndroidTest
```

#### Disable RUM integration

By default, flag evaluations are automatically sent to RUM (Real User Monitoring) and attached to the current view if RUM is enabled. You can disable this integration:

```kotlin
val flagsConfig = FlagsConfiguration.Builder()
    .rumIntegrationEnabled(false)
    .build()
```

**Note:** This setting only has an effect if you have enabled RUM (see [Initial setup](#initial-setup) section). If RUM is not enabled, flag evaluations are not sent to RUM regardless of this setting.

#### Disable exposure tracking

By default, flag evaluations are tracked and sent to Datadog's exposure intake endpoint. You can disable this:

```kotlin
val flagsConfig = FlagsConfiguration.Builder()
    .trackExposures(false)
    .build()
```

With `trackExposures(false)`, the SDK sends no exposure events to Datadog. It still records which exposures it has seen, so that the OpenFeature `ExposureHook` reports correct exposure decisions. See [Exposures in your own data store](../dd-sdk-android-flags-openfeature/README.md#exposures-in-your-own-data-store).

#### Configure custom endpoints

For testing or proxy purposes, you can configure custom endpoints:

```kotlin
val flagsConfig = FlagsConfiguration.Builder()
    .useCustomFlagEndpoint("https://your-proxy.example.com/flags")
    .useCustomExposureEndpoint("https://your-proxy.example.com/exposure")
    .build()
```

#### Load only selected flags

To load only the flags that your app uses, set a flag selection. The SDK sends it with the flag request, and the server returns only the flags that have any of the tags or whose key matches any of the key patterns:

```kotlin
val flagsConfig = FlagsConfiguration.Builder()
    .flagSelection(
        tags = listOf("mobile"),
        keyPatterns = listOf("checkout-*")
    )
    .build()
```

- Tags match exactly and are case-sensitive. A key pattern matches the whole flag key, and only `*` is a wildcard.
- Each list accepts at most 31 values, and each value is at most 200 UTF-8 bytes. The SDK does not send a selection that is over a limit. It logs an error and loads the full flag configuration.
- When the server applies the selection, evaluating a flag outside it returns the default value with `ErrorCode.FLAG_NOT_FOUND`.
- The flag selection is fixed when the Flags feature is enabled and applies to every `FlagsClient`. Flags cached under a different selection are not loaded at startup; until the next successful fetch, a client starts without cached flags.
- If the server ignores a selection, the SDK loads the full flag configuration and logs an error with the reason. A reason that the SDK does not know is reported as `unknown`.
- With a custom flag endpoint, the proxy must forward the `DD-Flag-Selection-Ignored` response header. Otherwise, the SDK cannot report a selection that the server ignored.
- With response signing, a verified response reports only the signed `DD-Flag-Selection-Ignored` value.

## Use the Feature Flags SDK

### Create a Flags client

After enabling the Feature Flags feature, create a `FlagsClient` to evaluate flags:

```kotlin
// Create a default client
val client = FlagsClient.Builder().build()

// Or create a named client for specific use cases
val analyticsClient = FlagsClient.Builder("analytics").build()
```

### Authorize assignment downloads for a subject

Subject authorization prevents the SDK from returning cached flag assignments for one subject (targeting key) while
the app requests assignments for another subject. If your environment requires subject authorization, get a JWT for
the subject from your backend. Then pass the complete evaluation context, including the token in the
`dd_assignment_token` attribute, in a single `setEvaluationContext` call:

```kotlin
client.setEvaluationContext(
    EvaluationContext(
        targetingKey = "user-123",
        attributes = mapOf(
            "country" to "US",
            "dd_assignment_token" to jwtFromYourBackend
        )
    )
)
```

Your backend signs the token for the requested subject and environment. Keep the private signing key on the backend.
Before the token expires, call `setEvaluationContext` again with the complete context and a new token. Changing only
the token triggers a full assignment download. To remove the token, omit `dd_assignment_token` or set it to an empty
string. The SDK does not restore an earlier token.

The SDK sends the token only in the reserved `DD-Assignment-Token` request header. It removes
`dd_assignment_token` from targeting attributes, persisted assignment context, exposure data, evaluation telemetry,
and SDK logs.

With a custom flag endpoint, the proxy must forward the `DD-Assignment-Token` header.

The SDK does not parse the token or validate its claims. The assignment endpoint validates its signature, expiration,
and other claims, so validation does not depend on the device clock. The token must contain only printable ASCII
characters and no whitespace. If the value is unusable, the SDK omits it, logs a fixed warning that does not include
the value, and sends the request without a token.

A usable token establishes a **subject boundary** for its targeting key as soon as the SDK accepts the context update:

- Retained assignments for a different targeting key are unavailable while the assignment download is in progress and
  after the download fails. Evaluation returns the application default with `ErrorCode.GENERAL`.
- Retained assignments for the same targeting key remain available as a fallback. The fallback keeps the context that
  was used to download the assignments, and the client state changes to `Stale`.
- [Experimentation opt-out modes](#exclude-a-subject-from-experimentation) still require an exact context match.

The SDK also establishes a subject boundary when the server returns an HTTP 401 response with the JSON:API error code
`ASSIGNMENT_AUTHORIZATION_FAILED`, even if the request had no token. The context callback receives
`FlagsAssignmentAuthorizationException` for this rejection. An eligible same-subject fallback remains available and
keeps the client `Stale`, but the callback still reports the failed refresh. Removing a token or retrying does not
restore another subject's assignments. A successful download or an eligible same-subject fallback clears the boundary.
The SDK does not automatically refresh tokens, retry the update, or verify signed assignment responses.

Without a token or an `ASSIGNMENT_AUTHORIZATION_FAILED` rejection, the SDK does not establish a subject boundary, and
evaluation works the same as in environments that do not require subject authorization. Missing, empty, and unusable
token values do not establish a boundary.

**Note**: `ErrorCode.GENERAL` is a public enum constant. Exhaustive Kotlin `when` expressions over `ErrorCode` must
handle it.

### Exclude a subject from experimentation

Configure the client to honor `dd_experimentation_opt_out_at`:

```kotlin
val client = FlagsClient.Builder()
    .experimentationOptOutMode(ExperimentationOptOutMode.IMMEDIATE)
    .build()

client.setEvaluationContext(
    EvaluationContext(
        targetingKey = "user-123",
        attributes = mapOf("dd_experimentation_opt_out_at" to "2026-10-21T10:00:00Z")
    ),
    object : EvaluationContextCallback {
        override fun onSuccess() {
            val enabled = client.resolveBooleanValue("new-feature", false)
            // Use the resolved value here.
        }

        override fun onFailure(error: Throwable) {
            // Keep the application's default behavior and handle the error here.
        }
    }
)
```

`setEvaluationContext` is asynchronous. A success callback means that fresh assignments are ready. A failure callback
can accompany the `Stale` state when eligible cached assignments remain available; inspect `client.state` before
deciding whether to continue using cached values.
Callbacks run on a background thread. Dispatch UI changes to the main thread.
A newer context can replace the assignments before a callback runs. In enabled modes, a request replaced
before its result is processed fails with `FlagsContextSupersededException`, unless its timeout already completed it.
Treat this exception as cancellation. OpenFeature maps it to coroutine cancellation instead of a provider error.
This applies whether the superseded fetch succeeds or fails. A result processed before replacement keeps its original outcome.
In `DISABLED`, previous assignments remain available and superseded requests retain their ordinary completion behavior.
With response signing enabled, a request that fails admission because a newer request started fails with
`FlagsContextSupersededException` in every opt-out mode, unless its callback has already completed.
The initialization timeout still applies to the newer request. `OBSERVE` keeps the previous assignments readable while a refresh is in flight.

| Mode | Behavior when the subject timestamp is present |
| --- | --- |
| `DISABLED` (default) | Ignore the timestamp. |
| `IMMEDIATE` | Exclude all experiment allocations, including experiments already in progress. |
| `FUTURE_EXPERIMENTS_ONLY` | Continue experiments activated before the timestamp. Exclude newer experiments and those with unknown activation times. |

Provide the customer's stored opt-out timestamp in the format below. Do not replace it with the current time on each launch.
In `IMMEDIATE` mode, any valid timestamp, including a future timestamp, excludes the subject from experiments now.
In `FUTURE_EXPERIMENTS_ONLY` mode, a future timestamp keeps experiments activated before that timestamp eligible.
Omit the attribute to permit experimentation.
Invalid timestamps return application defaults with `INVALID_CONTEXT` and do not send an assignment request.
An absent timestamp sends an ordinary assignment request without `evaluation_options`, including in enabled modes.

Timestamps use Gregorian calendar dates with years `0000` through `9999`, both before and after UTC normalization.
Use `YYYY-MM-DDTHH:mm:ss`, optional fractional seconds, and `Z` or a numeric `±HH:mm` offset
(hours `00`–`23`, minutes `00`–`59`). Lowercase `t` and `z` are accepted. Fractions beyond nine digits
are truncated without rounding, and comparisons preserve nanosecond precision. Leap seconds, invalid dates,
whitespace and named timezones are rejected.

The backend skips experiment allocations and continues through ordinary feature allocations.
The SDK requires the backend to acknowledge exclusion before using the response.
When the subject has no effective opt-out policy, responses explicitly marked as filtered are rejected.
Future mode requires the matching mode and exact cutoff in the acknowledgement.
In either enabled mode, assignments from a different context are unavailable, including for subjects without the timestamp attribute.
At startup, call `setEvaluationContext` with the matching context before the client can use persisted assignments.
Until matching assignments are available, evaluation returns the application default with `PROVIDER_NOT_READY`.
A malformed timestamp returns `INVALID_CONTEXT` instead. With `DISABLED`, persisted and previous assignments remain available.
Previously accepted assignments can be reused only for the same context, cutoff, and mode.
Each mode retains a separate disk cache. Changing modes does not delete another mode's cache.
Switching back can reuse that mode's older snapshot. In `DISABLED`, it is served as soon as persisted state loads,
for whichever subject it belonged to, until a successful context fetch replaces it. A failed fetch keeps it.
Call `setEvaluationContext` for the active subject; mode changes do not provide cache eviction.
`onFirstFlags` can report cached keys before an enabled-mode client has a matching requested context.
Those keys do not indicate readable values, and setting the context does not emit a second first-flags notification.
Evaluation telemetry is independent of experimentation opt-out.
Enable this option only after acknowledgement, ordinary fallback, and activation metadata are available on the customer's site.
If you use `useCustomFlagEndpoint`, the endpoint must preserve `data.attributes.evaluation_options` in both
requests and responses. Missing acknowledgement causes defaults for opted-out subjects without matching cached assignments.
Without acknowledgement or matching cached assignments, evaluation returns defaults with `PROVIDER_NOT_READY`.
A later successful `setEvaluationContext` call recovers the client. The SDK does not retry this operation automatically.

`ErrorCode.INVALID_CONTEXT` is a new public enum constant. Exhaustive Kotlin `when` expressions must handle it.
The context callback reports malformed timestamps through `FlagsInvalidContextException`.

Immediate withdrawal can change an enrolled subject's experience. Account for this change in experiment analysis.

### Control evaluation telemetry

Enable the local telemetry control when creating a client:

```kotlin
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.setEvaluationTelemetryOptOut

val client = FlagsClient.Builder("app")
    .enableEvaluationTelemetryOptOutParameter(true)
    .build()

// Your application owns this decision.
client.setEvaluationTelemetryOptOut(customerOptedOut)
```

When `customerOptedOut` is `true`, the client suppresses new evaluation events, counts, and automatic RUM flag annotations. Flag values and exposure logging remain unchanged.

The native targeting context contains strings. Use the typed setter above; a string `"true"` in the context does not activate opt-out. The [OpenFeature provider](../dd-sdk-android-flags-openfeature/README.md#control-evaluation-telemetry) accepts a boolean context attribute instead.

The `dd_evaluation_telemetry_opt_out` context attribute remains available for targeting and exposure reporting. Changing it through `setEvaluationContext` uses the normal assignment refresh path. Targeting rules can change assignments, and normal exposure deduplication applies.

The setter applies locally without fetching assignments or clearing cached values. Update the decision when the subject or your policy changes. Set it to `false` to resume evaluation telemetry. Activation defaults to disabled. Each named client retains its original configuration.

Previously recorded events, existing RUM annotations, manual annotations, and unrelated telemetry remain unchanged. Exposure events can still contain subject data. This control does not replace SDK tracking consent.

### Set evaluation context

Before evaluating flags, set the evaluation context with a targeting key and optional attributes:

```kotlin
val context = EvaluationContext(
    targetingKey = "user-123",
    attributes = mapOf(
        "email" to "user@example.com",
        "plan" to "premium",
        "age" to "25"
    )
)

client.setEvaluationContext(context)
```

**Notes**
- The targeting key must be consistent for the same user or entity to ensure consistent flag evaluation across requests. Common targeting keys include user ID, device ID, or session ID.
- For anonymous or unauthenticated users, use a **persistent UUID** as the targeting key:
  - **Proper traffic splitting**: A unique identifier ensures users are distributed correctly across flag variations.
  - **Consistent experience**: Persistence means the same user always sees the same flag values (consistent bucketing).
  - Generate the UUID once and persist it locally (for example, in `SharedPreferences`).
  - Transition to a user ID when the user authenticates.
- All attribute values must be strings. Convert numbers, booleans, and other types to strings before passing them.

### Evaluate feature flags

The `FlagsClient` provides two ways to resolve flag values:

- **Convenience methods** (`resolveBooleanValue`, `resolveStringValue`, etc.): Simple methods that return just the value
- **Detailed resolution method** (`resolve`): Returns comprehensive resolution details, including error information and metadata

#### Convenience methods

Use these methods when you only need the flag value:

**Boolean flags**
```kotlin
val isNewFeatureEnabled = client.resolveBooleanValue("new-feature-enabled", false)
```

**String flags**
```kotlin
val theme = client.resolveStringValue("app-theme", "light")
```

**Numeric flags**
```kotlin
// Integer values
val maxRetries = client.resolveIntValue("max-retry-count", 3)

// Double values
val discountPercentage = client.resolveDoubleValue("discount-rate", 0.0)
```

Any numeric flag can be read with either method. `resolveIntValue` returns the value only when it is a whole number within `Int` range, such as `5` or `5.0`. Otherwise, it returns the default value: `resolve()` reports `TYPE_MISMATCH` when the value is fractional, such as `1.2`, or outside `Int` range, such as `2147483648`, and `PARSE_ERROR` when the value can't be parsed. To bound parsing work, a value longer than 64 characters is not parsed unless it is a plain integer within `Int` range, so it reports `PARSE_ERROR`. `resolveDoubleValue` returns a fractional value as the nearest `Double`, such as `1.2`. It returns a whole value only when the `Double` has exactly that value. For other whole values, such as `9007199254740993` or `1e300`, `resolve()` reports `TYPE_MISMATCH`. It also reports `TYPE_MISMATCH` for a nonzero value that rounds to zero, such as `1e-400`. `NaN` and `Infinity` report `PARSE_ERROR`. The same applies to a value longer than 64 characters whose `Double` is zero or 2^53 or more in magnitude.

**Structured flags**
```kotlin
val defaultConfig = JSONObject("""{"timeout": 30, "retries": 3}""")
val config = client.resolveStructureValue("api-config", defaultConfig)

val timeout = config.getInt("timeout")
val retries = config.getInt("retries")
```

#### Detailed resolution method

Use the `resolve()` method when you need additional information about flag resolution, such as:
- The variant identifier (for example, "control", "treatment")
- The reason for the resolved value (for example, `TARGETING_MATCH`, `DEFAULT`, `ERROR`)
- Error codes and messages for debugging
- Flag metadata for analytics

```kotlin
val result = client.resolve("feature-enabled", false)

// Access the resolved value
val featureEnabled = result.value

// Check for errors
if (result.errorCode != null) {
    println("Flag resolution failed: ${result.errorMessage}")
} else {
    println("Flag resolved successfully")
    
    // Access variant information
    result.variant?.let { variant ->
        println("Variant: $variant")
    }
    
    // Access resolution reason
    result.reason?.let { reason ->
        println("Reason: $reason")
    }
    
    // Access flag metadata
    result.flagMetadata?.forEach { (key, value) ->
        println("Metadata: $key = $value")
    }
}
```

##### Detailed resolution properties
- `value: T` - The resolved flag value (always present, either from evaluation or default)
- `variant: String?` - Optional identifier for the resolved variant
- `reason: String?` - Optional explanation of why this value was resolved
- `errorCode: ErrorCode?` - Optional error code (null indicates success)
- `errorMessage: String?` - Optional human-readable error message
- `flagMetadata: Map<String, Any>?` - Optional metadata associated with the flag

##### Split features

When the selected split has features for application hooks, `flagMetadata` contains one entry for each of them, with the key `__dd_feature.<feature key>`. For example, a holdout gives `__dd_feature.holdout.key`, `__dd_feature.holdout.assignment_group` and `__dd_feature.holdout.should_include_in_holdout_analysis`. A value is a `String`, `Double` or `Boolean`. Other `__dd_` entries are internal and can change without notice.

##### Error codes
- `FLAG_NOT_FOUND` - The flag could not be found
- `PARSE_ERROR` - Error parsing the flag value
- `TYPE_MISMATCH` - The flag value's type doesn't match the expected type, such as a fractional value read with `resolveIntValue`
- `TARGETING_KEY_MISSING` - No targeting key was provided
- `INVALID_CONTEXT` - The evaluation context is invalid
- `PROVIDER_NOT_READY` - The provider is not yet ready
- `PROVIDER_FATAL` - The provider encountered a fatal error
- `GENERAL` - A general error occurred

### Retrieve existing clients

You can retrieve a previously created client by name:

```kotlin
// Retrieve the default client
val client = FlagsClient.get()

// Retrieve a named client
val analyticsClient = FlagsClient.get("analytics")
```

**Note:** If you call `get()` before calling `build()` for that client name, a no-op client is returned that always returns default values and logs an error.

### Wait for the first flags

`client.onFirstFlags` notifies each registration once, when the client installs its first flags from disk
cache or the network. This is one notification per registration, containing all keys in that first
accepted configuration (possibly empty), not one callback per flag or an inventory of all server-side flags.
For an Activity that owns a `client`:

```kotlin
import com.datadog.android.flags.FlagsSubscription
import timber.log.Timber

private var firstFlagsSubscription: FlagsSubscription? = null

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    firstFlagsSubscription = client.onFirstFlags { event ->
        Timber.i("First installed flag keys: %s", event.flagsChanged)
    }
}

override fun onDestroy() {
    firstFlagsSubscription?.unsubscribe()
    super.onDestroy()
}
```

- An available first result replays from retained memory without I/O, synchronously before registration
  returns. Pending callbacks run
  on a dedicated background thread. Dispatch UI work to the main thread.
- Every registration receives the retained first result, even after later updates. Evaluations read
  current flags, which may have changed since the event. Delivery order across registrations is not
  guaranteed: a later registration may be notified before an earlier pending one.
- Missing or unreadable cache and malformed network responses do not trigger the callback. A valid
  empty configuration does trigger it with an empty key list.
- Unsubscription is thread-safe and idempotent and releases pending captures. A callback already
  claimed for delivery may still run; cancellation does not interrupt it or undo synchronous replay.
- This is not a readiness signal. In enabled opt-out modes, cached keys can arrive before any flags are readable.
  Call `setEvaluationContext` and use `client.state` to track readiness. No second first-flags event follows context registration.
- Ordinary callback exceptions are logged and isolated.

Use `client.onFirstFlags { event -> ... }` from Kotlin or
`client.onFirstFlags(event -> { ... })` from Java. The client retains the first installed keys
and creates a separate event with a key-list copy for each delivery. Evaluations inside the callback
read its current flags. Custom `FlagsClient` implementations
must implement `onFirstFlags`; Kotlin interface delegation forwards it to the delegate.

The listener and subscription are Kotlin functional interfaces: `FlagsClientEventListener.onEvent`
and `FlagsSubscription.unsubscribe`.
`FlagsClientEvent` is constructed internally by the SDK, snapshots its key list, and currently supports
`CONFIGURATION_CHANGED`. Applications receive events through the listener.

The [Kotlin sample](../../sample/kotlin/src/main/kotlin/com/datadog/android/sample/SampleApplication.kt)
registers an application-lifetime callback, logs keys, and evaluates `"my-flag-key"` with default `false`.

## Evaluation telemetry privacy

Evaluation telemetry protects targeting keys by default. Only a literal boolean `true` in the
assignment response's `data.attributes.observeFullEvaluationData` enables raw targeting keys.
Absent, false, null, or malformed consent values keep protection enabled without rejecting the
assignments. Consent is cached with the assignments and changes on normal configuration refresh.
Evaluations queued before a refresh retain the consent of the configuration they used.
Experimentation opt-out does not change this consent setting. Errors for a pending or invalid
requested context use consent from the latest accepted assignments response and the requested
subject's targeting key. Before any accepted configuration is available, protection defaults to enabled.

Protected nonempty targeting keys are SHA-256 hashes of the exact UTF-8 text, prefixed with
`sha256_`. Empty keys remain empty; missing keys and malformed Unicode are omitted without losing
the evaluation in both consent modes. Valid Unicode is preserved exactly, without normalization.
Android continues to omit `context.evaluation` in both modes. The consent setting does
not change evaluation inputs, assignment requests, exposure events, or RUM integration.

Evaluation telemetry error messages contain stable error codes in both consent modes, with
`GENERAL` for unknown failures. `trackEvaluations(false)` disables this track. Assignment `doLog`
controls exposures independently.

## Usage attribution

Flag requests send the SDK's `service` in the `dd-service` header, so Usage Attribution can split Feature Flags
usage by `service`. It is the same service RUM reports: the one set in the core `Configuration`, or else the
package name. A service that is not a valid HTTP header value is not sent, and the request still goes out.

## Integration with RUM

When RUM is enabled in your application and RUM integration is enabled in the Flags configuration (default), flag evaluations are automatically:
- Attached to the current RUM view
- Visible in the Datadog RUM dashboard
- Associated with user sessions for analysis

This allows you to correlate feature flag usage with application performance, errors, and user behavior.

## Prerequisites for RUM integration
1. Add the `dd-sdk-android-rum` dependency to your project
2. Enable RUM before initializing the Flags feature (see [Initial setup](#initial-setup) section)
3. Ensure `rumIntegrationEnabled` is set to `true` in your `FlagsConfiguration` (this is the default)

If RUM is not enabled, the Flags SDK will continue to work normally, but flag evaluations will not appear in RUM views.

## Best practices

- **Consistent targeting keys**: Use consistent targeting keys (for example, user ID) to ensure users see consistent feature flag values across sessions.
- **Provide meaningful defaults**: Always provide sensible default values that maintain core functionality if flag evaluation fails.
- **Set context early**: Set the evaluation context as early as possible in your application lifecycle, typically after user authentication.
- **Use named clients**: Use named clients if necessary to organize flags by domain (for example, "analytics", "ui", "experiments")
- **Convert values to strings**: Remember to convert all attribute values to strings before passing them to `EvaluationContext`.

## Further reading

For more information on Feature Flags in Datadog, see the [official Feature Flags documentation][2].

[1]: https://docs.datadoghq.com/real_user_monitoring/application_monitoring/android/setup
[2]: https://docs.datadoghq.com/getting_started/feature_flags/

## Shared precomputed response tests

The Flags module uses the JSON contract from [ffe-system-test-data](https://github.com/DataDog/ffe-system-test-data/tree/main/precomputed-assignments).
This keeps response behavior consistent across mobile SDKs. The snapshot is test data only.
It adds no runtime dependency and requires no Git submodule.

`ci/feature-flags-precomputed-fixtures.json` lists the upstream sources. Each source has its own path, destination, format and pinned commit, so later fixture sets reuse the same pipeline. The `precomputed-assignments` source is read from the git-ignored `reliability/single-fit/flags/src/test/resources/precomputed` folder. `SOURCE.json` in each destination records the source commit, the source path and file map, and the file digests. If the path or file map changes, the next run imports the source again.
The runner discovers all nested JSON cases through the generated index.
Adding a case requires an upstream fixture change and a pin update. It requires no runner or filename-list change.
Missing or empty fixtures fail the suite. Shared `_include` and `_skip` fields describe API applicability.
Do not add local exclusions to hide failures.

Numeric behavior follows the [shared numeric contract](https://github.com/DataDog/ffe-system-test-data/blob/main/precomputed-assignments/README.md#mobile-conformance-and-numeric-types).
The SDK keeps each numeric token as written in the response, also in the cache. An Int read of a fractional token, such as `4503599627370497.5` or `1.00000000000000001`, returns the default with `TYPE_MISMATCH`.
Assignment snapshots for cross-platform bridges omit a numeric assignment that no Int or Double read accepts, such as `1e400`, `1e-400`, or `100000000000000000001`.
Plaintext flag keys match exactly, without Unicode normalization. Exposure and evaluation events use the key that the caller passed.
The native context API requires a string targeting key. Fixtures with `targetingKey: null` must declare this platform in `_skip` upstream; the runner reports an actionable fixture error if an applicable case violates that requirement.

Upstream CI requires a generated obfuscated companion for each applicable plain fixture. It rejects missing, stale, or orphaned companions. Edit the plain case upstream and regenerate; do not maintain two sets of expectations. Dedicated malformed-encoding cases and fixed digest vectors remain separate.

Cases cover values, defaults, errors, reasons, variants, emission gates, duplicate evaluations, and numeric boundaries.
Obfuscation cases cover fixed digests, salts, Unicode, whitespace, malformed metadata, and plaintext event identity.
The suite uses the feature integration harness. Each fixture also runs through required signature verification. The suite feeds the fixture response, with its numeric tokens and `observeFullEvaluationData` value, through the real download, parse, and persistence path. Valid unsigned fixtures run after cache restoration without networking. The suite checks public getters, response rejection, and real exposure and evaluation records.
Matchers compare JSON types strictly: `true` does not match `1`. A `_count` that is not a positive integer fails the test.
The suite prints a report with the fixture revision, the executed evaluations, and the exclusions.

Run from the repository root:

```sh
./gradlew :reliability:single-fit:flags:testReleaseUnitTest --tests '*CanonicalPrecomputedTest*'
python3 ci/test-sync-feature-flags-precomputed-fixtures.py
```

The `pullFeatureFlagsFixtures` task runs only before unit-test resources are processed. It fetches the pinned commit and verifies its digests. When the local snapshot already matches the pin, it only verifies, without networking.
Each fetch attempt has a timeout, and a failed attempt is retried. The task has a 10-minute timeout.
The snapshot check ignores `.DS_Store` files. Any other file that is not in the snapshot fails the check.

Move the pin to a reviewed upstream commit:

```sh
python3 ci/sync-feature-flags-precomputed-fixtures.py --update --ref <full-upstream-sha>
```

The importer copies committed bytes, regenerates the index, and removes deleted cases.
It preserves Unity asset GUIDs where applicable. Do not edit fetched JSON or its checksums.
Use `--update --source <local-checkout> --ref <commit>` to test an upstream fixture branch before publication.
`--verify` checks that the local snapshot matches the pin without networking.

The weekly `Feature flags precomputed fixtures` workflow opens a draft update PR that moves the pin when fixture content changes.
It runs only in `DataDog/dd-sdk-android`, not in forks.
Scheduled and manual updates on `develop` share one concurrency group.
It requests a short-lived write token only when the pin changed. The token is restricted to the default branch and this workflow.
If PR creation fails, the workflow retains the signed update branch for inspection and recovery.
A PR that changes only the pin runs the `test:single-fit-flags` GitLab job, which runs this suite.
It waits when an update PR is already open. Normal SDK CI must pass before merge.
Merge the upstream fixture contract before merging its consumer PRs.
The update workflow never merges or publishes an SDK release.
