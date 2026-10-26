# Android Flags integration tests

This JVM suite exercises the built native Flags SDK and Kotlin OpenFeature provider with the
existing `StubSDKCore`. Assignment responses and storage are controlled by the harness. Privacy
assertions inspect serialized events and the actual evaluations request factory's NDJSON body,
including a retry built from the same stored batch. No emulator or live backend is required.

## Run with project dependencies

```sh
./gradlew :features:dd-sdk-android-flags:testDebugUnitTest \
  :features:dd-sdk-android-flags-openfeature:testDebugUnitTest \
  :reliability:single-fit:flags:testReleaseUnitTest
```

## Run against exact release AARs

Build both modules, copy their artifacts to one directory, and select that directory for the
integration test dependencies. This replaces the two Flags project dependencies with the supplied
AARs; the core and RUM test dependencies remain local projects.

```sh
./gradlew :features:dd-sdk-android-flags:assembleRelease \
  :features:dd-sdk-android-flags-openfeature:assembleRelease
mkdir -p /tmp/flags-artifacts
cp features/dd-sdk-android-flags/build/outputs/aar/dd-sdk-android-flags-release.aar /tmp/flags-artifacts/
cp features/dd-sdk-android-flags-openfeature/build/outputs/aar/dd-sdk-android-flags-openfeature-release.aar /tmp/flags-artifacts/
sha256sum /tmp/flags-artifacts/*.aar
./gradlew :reliability:single-fit:flags:testReleaseUnitTest \
  -PflagsArtifactDirectory=/tmp/flags-artifacts
```

`FlagsPrivacyTest` covers direct SDK and OpenFeature evaluation, the five consent states, mixed
consent before shutdown flush, stable error/default counts, context omission, original assignment
inputs, offline cache restoration, failed refresh, retry payloads, missing/empty/malformed targeting
keys in both consent modes, valid Unicode including surrogate pairs, object-result metadata, tracking
disable, and retained native snapshots. `FlagsBridgePrivacyTest` exercises both evaluation-only
bridge overloads with retained consent, successful and default/error evaluations, unknown error
codes, distinct omitted and empty telemetry keys, and no exposure or RUM side effects. The bridge
fixtures use `doLog=true` to prove that evaluation-only hooks do not trigger exposure logging.
`FlagsPrivacyTest` uses `doLog=false` to verify that it cannot suppress evaluation telemetry. Existing
processor unit tests cover concurrent recording, draining, periodic flush and shutdown.

Only precomputed evaluation is supported by this implementation. These tests do not claim a
rules-based evaluator, live backend acceptance, or transport retry scheduling coverage.

## Run on an Android device or emulator

`FlagsPrivacyInstrumentedTest` runs the release SDK and provider on ART with the real SDK core,
file persistence, scheduled aggregation, upload scheduling and a loopback HTTP server. It covers
five-state consent parsing, protected/full buckets across refresh, stable errors, context omission,
malformed/empty keys and valid Unicode in both consent modes, tracking opt-out, periodic upload
with a 503 retry, and consent and malformed keys restored from disk after restarting the SDK and
failing a refresh. There are 16 parameterized cases per device. Both native and OpenFeature paths are covered;
the offline-restart cases call the provider directly to isolate cache behavior from OpenFeature's
failed-initialization policy. The shutdown cases invoke the feature's stop callback and the core's
existing test drain hook; the retry cases use normal scheduled uploads.

With a booted emulator or connected device:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :reliability:single-fit:flags:connectedReleaseAndroidTest
```

The existing `test-pyramid:core-it-{min,median,latest}-api` CI jobs also run this suite on Android
API 23, 28 and 37 emulators. Each job builds and copies the native/provider release AARs, logs their
SHA-256 hashes and supplies them through `-PflagsArtifactDirectory` to the device suite. They upload
the instrumentation results from
`reliability/single-fit/flags/build/outputs/androidTest-results/connected/release/`.
The tests use synthetic credentials and a local server; no live Datadog org is needed. They verify
actual Android HTTP uploads, but do not claim live backend acceptance or process-kill recovery.

## React Native handoff

Native Android owns evaluation telemetry protection. The internal assignment snapshot can
implement `UnparsedFlagWithConsent`; its `observeFullEvaluationData` value belongs to that snapshot.
React Native must serialize this boolean in `convertUnparsedFlagToMap`, preserve it in its JS cache,
and implement `UnparsedFlagWithConsent` when reconstructing the assignment in
`convertMapToUnparsedFlag`. Only literal `true` opts in. Replaying through
`_FlagsInternalProxy.trackFlagSnapshotEvaluation` must use that captured value, never current
configuration or a JS-side hash. Its nullable telemetry targeting key parameter is separate from
the context used for exposures and subject opt-out.

Native Android owns telemetry validation and hashing. The installed configuration precomputes
its telemetry identity, so repeated flag evaluations reuse it. Retained evaluations keep that
identity across context or consent refreshes. Bridge replay uses a single cached identity per
client, independent of the active configuration.

The separate `_FlagsInternalProxy.trackFlagEvaluationEvent` hook has an overload accepting
`observeFullEvaluationData` and a nullable `telemetryTargetingKey`. Pass the consent captured at
evaluation time, including errors without an assignment, rather than the current native
configuration. Pass null for an absent telemetry identity or an empty string for an explicitly
empty identity. The original four-argument overload derives consent from the supplied flag and
defaults to protected mode when none is available.
Neither overload emits exposures or RUM annotations. Use the separate hooks for those tracks.

Existing implementations of `UnparsedFlag` remain ABI-compatible and replay in protected mode.
Native success/error metadata also carries `__dd_observe_full_evaluation_data`. Android and the
provider must ship together as validated artifacts; React Native must consume and validate the
protected native release before shipping its integration.
