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
keys, object-result metadata, tracking disable, and retained native snapshots. `doLog=false` is
used throughout these privacy fixtures to ensure it cannot suppress evaluation telemetry. Existing
processor unit tests cover concurrent recording, draining, periodic flush and shutdown.

Only precomputed evaluation is supported by this implementation. These tests do not claim a
rules-based evaluator, live backend acceptance, or transport retry scheduling coverage.

## React Native handoff

Native Android owns evaluation telemetry protection. The internal assignment snapshot can now
implement `UnparsedFlagWithConsent`; its `observeFullEvaluationData` value belongs to that snapshot.
React Native must serialize this boolean in `convertUnparsedFlagToMap`, preserve it in its JS cache,
and implement `UnparsedFlagWithConsent` when reconstructing the assignment in
`convertMapToUnparsedFlag`. Only literal `true` opts in. Replaying through
`_FlagsInternalProxy.trackFlagSnapshotEvaluation` must use that captured value, never current
configuration or a JS-side hash.

Existing implementations of `UnparsedFlag` remain ABI-compatible and replay in protected mode.
Native success/error metadata also carries `__dd_observe_full_evaluation_data`. Android and the
provider must ship together as validated artifacts; React Native must consume and validate the
protected native release before shipping its integration. SDK-owner review, release cutoff and
published dependency versions remain release-owner decisions tracked by FFL-3437.
