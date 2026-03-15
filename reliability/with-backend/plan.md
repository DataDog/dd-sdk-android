# RumViewUpdateTest Coverage Plan

## Already Tested ✅

| Field | How |
|---|---|
| `view.id` | Used for querying |
| `view.name` | `hasViewName()` |
| `view.isActive` | `isNotActive()` |
| `view.timeSpent` | `hasTimeSpent()` — exact match vs local |
| `view.cpuTicksCount` | `hasCpuTicksCount()` — exact match vs local |
| `view.action.count` | `hasActionCount(2)` |
| `view.error.count` | `hasErrorCount(3)` |
| `view.resource.count` | `hasResourceCount(1)` |
| `view.customTimings.screen_loaded` | `hasCustomTiming("screen_loaded")` |
| `featureFlags.flag_bool` | `hasFeatureFlagBoolean("flag_bool", false)` |
| `featureFlags.flag_int` | `hasFeatureFlagInt("flag_int", 100)` |
| `context.custom_attr` | `hasContextAttribute("custom_attr", "hello_world")` via `addAttribute` |
| `usr.anonymousId` | `hasAnonymousUserIdNonNull()` |
| `application.id` | `hasApplicationId()` — exact match vs local |
| `session.id` | `hasSessionId()` — exact match vs local |

---

## Tier 1 — Cross-check auto-populated fields

No test scenario changes required. Only add backend model fields and assertions cross-checked against local `ViewEvent`.

| Field | Strategy | Backend model change? |
|---|---|---|
| `view.url` | Exact match vs `localEvent.view.url` | No — already in `ViewAttributes` |
| `view.cpuTicksPerSecond` | Null-safe match vs `localEvent.view.cpuTicksPerSecond` | No — already in `ViewAttributes` |
| `view.memoryAverage` | Null-safe match vs `localEvent.view.memoryAverage` | Add to `ViewAttributes` |
| `view.memoryMax` | Null-safe match vs `localEvent.view.memoryMax` | Add to `ViewAttributes` |
| `view.refreshRateAverage` | Null-safe match vs `localEvent.view.refreshRateAverage` | Add to `ViewAttributes` |
| `view.refreshRateMin` | Null-safe match vs `localEvent.view.refreshRateMin` | Add to `ViewAttributes` |
| `os.name` | Exact match vs `localEvent.os.name` | No — already in `Os` |
| `os.version` | Exact match vs `localEvent.os.version` | No — already in `Os` |
| `os.versionMajor` | Exact match vs `localEvent.os.versionMajor` | No — already in `Os` |
| `device.name` | Null-safe match vs `localEvent.device.name` | No — already in `Device` |
| `device.model` | Null-safe match vs `localEvent.device.model` | No — already in `Device` |
| `device.brand` | Null-safe match vs `localEvent.device.brand` | No — already in `Device` |
| `device.architecture` | Null-safe match vs `localEvent.device.architecture` | No — already in `Device` |
| `device.locale` | Null-safe match vs `localEvent.device.locale` | No — already in `Device` |
| `device.timeZone` | Null-safe match vs `localEvent.device.timeZone` | No — already in `Device` |
| `connectivity.status` | Assert non-null (device is connected during test run) | No |
| `session.type` | Assert `"user"` — normal sessions always have this type | No |
| `application.currentLocale` | Null-safe match vs `localEvent.application.currentLocale` | No — already in `Application` |
| `version` | Null-safe match vs `localEvent.version` | Add `version: String?` to top-level backend model |
| `buildVersion` | Null-safe match vs `localEvent.buildVersion` | Add `buildVersion: String?` to top-level backend model |

---

## Tier 2 — Small test setup additions

| Field | Strategy | Changes needed |
|---|---|---|
| `usr.id`, `usr.name`, `usr.email` | Add `Datadog.setUserInfo("test-id", "Test User", "test@example.com")` to `BaseRumViewTest.setUpBase()`. Assert all three on backend. | Add `id: String?`, `name: String?`, `email: String?` to `RumSearchResponse.Usr` |
| `service` | Add `.setService("test-service")` to `Configuration.Builder` in base. Assert `actual.attributes.service == "test-service"`. | No — `service` already in `RumEventAttributes` |
| `view.loadingTime` | Call `@OptIn(ExperimentalRumApi::class) rumMonitor.addViewLoadingTime(false)` in test. Does not add a new view index. Assert `loadingTime` is non-null and > 0. | Add `loadingTime: Long?` to `ViewAttributes` |
| `view.networkSettledTime` | Auto-computed after resources settle. Assert null-safe match vs `localEvent.view.networkSettledTime`. | Add `networkSettledTime: Long?` to `ViewAttributes` |
| `date` | Assert `localEvent.date` matches backend `timestamp` (convert ISO-8601 backend string to epoch ms). | Use existing `RumEventAttributes.timestamp` |
| `dd.documentVersion` | Assert `localEvent.dd.documentVersion == backend _dd.document_version`. For view index 12 this should be 13 (1-indexed). | Add `_dd` object with `documentVersion: Long` to backend model |

---

## Tier 3 — Harder to trigger

| Field | Strategy | Notes |
|---|---|---|
| `view.longTask.count` | `withContext(Dispatchers.Main) { Thread.sleep(300) }` — blocks UI thread > 250ms threshold, SDK auto-detects a long task. Adds 1 view update; final index shifts from 12 → 13. | Adjust all index references |
| `view.accessibility.*` | Already have `collectAccessibility(true)`. Read `localEvent.view.accessibility` and cross-check each field vs backend. Extend `RumSearchResponse.Accessibility` with missing fields. | Depends on device accessibility settings |
| `view.frustration.count` | Call `startAction` then `addError` within the same action window — error actions count as frustration signals. | Timing-sensitive; may not be reliable in CI |

---

## Not Applicable / Skip

| Field | Reason |
|---|---|
| `synthetics`, `ciTest` | Only populated in Synthetics / CI Visibility test runs |
| `container` | Only for embedded WebViews |
| `display.viewport`, `display.scroll` | Web-only |
| `view.firstContentfulPaint`, `largestContentfulPaint`, CLS, FID, INP | Web vitals, not populated on Android native |
| `view.loadingType` | Web navigation types (initial_load, route_change, etc.) |
| `view.performance` | Web vitals schema |
| `view.flutterBuildTime`, `flutterRasterTime`, `jsRefreshRate` | Flutter / React Native only |
| `privacy` | Requires Session Replay |
| `session.hasReplay`, `session.sampledForReplay` | Requires Session Replay |
