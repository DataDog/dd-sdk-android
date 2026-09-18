# reliability/with-backend

End-to-end tests that verify the full SDK → intake → backend pipeline by sending real events to a Datadog staging environment and querying the API to assert the expected data was received and processed correctly.

## Purpose

Unlike `reliability/single-fit` which tests the SDK in-process using a stub writer, this module initializes the real SDK, uploads events over the network, and verifies the backend correctly stores and reconstructs the final view state. This is the only test suite that catches issues in the intake pipeline, backend merge semantics, or API response format.

## Running the tests

Requires a connected device or emulator and valid Datadog staging credentials passed as instrumentation arguments:

```bash
./gradlew :reliability:with-backend:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.DD_CLIENT_TOKEN="<client_token>" \
  -Pandroid.testInstrumentationRunnerArguments.DD_RUM_APP_ID="<rum_app_id>" \
  -Pandroid.testInstrumentationRunnerArguments.DD_API_KEY="<api_key>" \
  -Pandroid.testInstrumentationRunnerArguments.DD_APP_KEY="<app_key>"
```

The tests use `DatadogSite.STAGING` and query `https://dd.datad0g.com`. Credentials must have access to the staging environment.

## Test suite

| Test | What it verifies |
|------|-----------------|
| `RumViewUpdateTest` | The backend correctly reconstructs the final view state (counts, feature flags, custom timings, performance, user info) after receiving a full `ViewEvent` followed by a series of `ViewUpdateEvent` diffs |
