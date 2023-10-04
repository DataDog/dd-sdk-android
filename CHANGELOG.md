# 2.2.0 / 2023-10-04

* [FEATURE] Session Replay: Serialize TextViews/Buttons to base64. See [#1592](https://github.com/DataDog/dd-sdk-android/pull/1592)
* [FEATURE] WebView Tracking: Add sampler to `WebViewLogEventConsumer`. See [#1629](https://github.com/DataDog/dd-sdk-android/pull/1629)
* [FEATURE] RUM: Add cross platform GraphQL attributes. See [#1631](https://github.com/DataDog/dd-sdk-android/pull/1631)
* [FEATURE] Trace: Add `networkInfoEnabled` option in `TraceConfiguration`. See [#1636](https://github.com/DataDog/dd-sdk-android/pull/1636)
* [FEATURE] Logs: Add `isEnabled` to Logs. See [#1648](https://github.com/DataDog/dd-sdk-android/pull/1648)
* [BUGFIX] Session Replay: Fix `RippleDrawables`. See [#1600](https://github.com/DataDog/dd-sdk-android/pull/1600)
* [BUGFIX] Session Replay: Fix Base64 issues with multithreading. See [#1613](https://github.com/DataDog/dd-sdk-android/pull/1613)
* [BUGFIX] RUM: Treat scroll on non-scrollable view as tap. See [#1622](https://github.com/DataDog/dd-sdk-android/pull/1622)
* [BUGFIX] Trace: Fix `PendingTrace` `ConcurrentModificationException`. See [#1623](https://github.com/DataDog/dd-sdk-android/pull/1623)
* [BUGFIX] RUM: Propagate session state and view type as Strings. See [#1625](https://github.com/DataDog/dd-sdk-android/pull/1625)
* [BUGFIX] Fix the WebView fragment in sample app. See [#1627](https://github.com/DataDog/dd-sdk-android/pull/1627)
* [BUGFIX] RUM: Prevent NPE in `GestureListener`. See [#1634](https://github.com/DataDog/dd-sdk-android/pull/1634)
* [BUGFIX] RUM: Fix duplicate views in `MixedViewTrackingStrategy`. See [#1639](https://github.com/DataDog/dd-sdk-android/pull/1639)
* [BUGFIX] Telemetry: Fix the batch duration value in `batch_close` telemetry event. See [#1633](https://github.com/DataDog/dd-sdk-android/pull/1633)
* [BUGFIX] Global: Make `FeatureFileOrchesrator` use file persistence config created from user/feature settings. See [#1643](https://github.com/DataDog/dd-sdk-android/pull/1643)
* [BUGFIX] Telemetry: Fix RegEx in `FeatureFileOrchestrator` to resolve file consent type. See [#1645](https://github.com/DataDog/dd-sdk-android/pull/1645)
* [IMPROVEMENT] Session Replay: Base64 Caching Mechanism. See [#1534](https://github.com/DataDog/dd-sdk-android/pull/1534)
* [IMPROVEMENT] Session Replay: Implement bitmap downscaling. See [#1546](https://github.com/DataDog/dd-sdk-android/pull/1546)
* [IMPROVEMENT] Session Replay: Implement pool of reusable bitmaps. See [#1554](https://github.com/DataDog/dd-sdk-android/pull/1554)
* [IMPROVEMENT] Session Replay: Refactor caches from singletons to class instances. See [#1564](https://github.com/DataDog/dd-sdk-android/pull/1564)
* [IMPROVEMENT] Session Replay: Optimize bitmap processing. See [#1576](https://github.com/DataDog/dd-sdk-android/pull/1576)
* [IMPROVEMENT] Session Replay: Add the Session Replay functional tests for sensitive input fields. See [#1601](https://github.com/DataDog/dd-sdk-android/pull/1601)
* [IMPROVEMENT] Session Replay: Add the Session Replay functional tests for checkboxes and radiobuttons. See [#1609](https://github.com/DataDog/dd-sdk-android/pull/1609)
* [IMPROVEMENT] Add sample showing how to listen to memory events. See [#1621](https://github.com/DataDog/dd-sdk-android/pull/1621))
* [IMPROVEMENT] WebView Tracking: Only send Webview RUM events when Native Session exists and is tracked. See [#1626](https://github.com/DataDog/dd-sdk-android/pull/1626)
* [IMPROVEMENT] Session Replay: Fix the async image loading logic inside the Session Replay view mappers. See [#1619](https://github.com/DataDog/dd-sdk-android/pull/1619)
* [IMPROVEMENT] RUM: Let exceptions from `Window.Callback` to propagate. See [#1632](https://github.com/DataDog/dd-sdk-android/pull/1632)
* [IMPROVEMENT] Session Replay: Add Session Replay functional tests for `ImageButtons` and `ImageViews`. See [#1630](https://github.com/DataDog/dd-sdk-android/pull/1630)
* [IMPROVEMENT] Trace: Make network info optional in span schema. See [#1635](https://github.com/DataDog/dd-sdk-android/pull/1635)
* [IMPROVEMENT] Trace: Use `networkInfoEnabled` to serialize or not network info in spans. See [#1637](https://github.com/DataDog/dd-sdk-android/pull/1637)
* [IMPROVEMENT] Telemetry: Add more information into the batch telemetry. See [#1641](https://github.com/DataDog/dd-sdk-android/pull/1641)
* [IMPROVEMENT] Session Replay: Implement heuristic image classification. See [#1640](https://github.com/DataDog/dd-sdk-android/pull/1640)
* [IMPROVEMENT] Global: `DataUploadWorker` is scheduled every time and on non-roaming network. See [#1647](https://github.com/DataDog/dd-sdk-android/pull/1647)
* [IMPROVEMENT] RUM: Use enum for HTTP method parameter of `RumMonitor#startResource API`. See [#1653](https://github.com/DataDog/dd-sdk-android/pull/1653)
* [MAINTENANCE] Align the Base64 feature branch with develop. See [#1594](https://github.com/DataDog/dd-sdk-android/pull/1594)
* [MAINTENANCE] Integrate latest changes from develop into base64 feature. See [#1599](https://github.com/DataDog/dd-sdk-android/pull/1599)
* [MAINTENANCE] Base64 feature branch integration. See [#1597](https://github.com/DataDog/dd-sdk-android/pull/1597)
* [MAINTENANCE] Session Replay: Implement the Session Replay payloads update `local_ci` task. See [#1598](https://github.com/DataDog/dd-sdk-android/pull/1598)
* [MAINTENANCE] Fix Android Studio 'Rebuild Project'. See [#1602](https://github.com/DataDog/dd-sdk-android/pull/1602)
* [MAINTENANCE] Next dev iteration 2.2.0. See [#1604](https://github.com/DataDog/dd-sdk-android/pull/1604)
* [MAINTENANCE] Merge release 2.1.0 into develop branch. See [#1607](https://github.com/DataDog/dd-sdk-android/pull/1607)
* [MAINTENANCE] Use shared Android Lint check. See [#1608](https://github.com/DataDog/dd-sdk-android/pull/1608)
* [MAINTENANCE] Provide session replay data in configuration telemetry. See [#1611](https://github.com/DataDog/dd-sdk-android/pull/1611)
* [MAINTENANCE] Fix unit test issues caused by git merge. See [#1618](https://github.com/DataDog/dd-sdk-android/pull/1618)
* [MAINTENANCE] Session Replay: Update functional tests due to `ImageView` support. See [#1646](https://github.com/DataDog/dd-sdk-android/pull/1646)
* [MAINTENANCE] Target Android 14 (API 34). See [#1649](https://github.com/DataDog/dd-sdk-android/pull/1649)

# 2.1.0 / 2023-09-07

* [BUGFIX] Session Replay: Do not resolve `WindowManager` from `Application` context. See [#1558](https://github.com/DataDog/dd-sdk-android/pull/1558)
* [BUGFIX] RUM: Report `ApplicationLaunch` view even if first RUM event is not interaction. See [#1591](https://github.com/DataDog/dd-sdk-android/pull/1591)
* [BUGFIX] RUM: Fix crash when disabling `JankStats` tracking. See [#1596](https://github.com/DataDog/dd-sdk-android/pull/1596)
* [IMPROVEMENT] RUM: Add sample rate to reported RUM events. See [#1566](https://github.com/DataDog/dd-sdk-android/pull/1566)
* [IMPROVEMENT] Session Replay: Remove the query parameters from SR requests. See [#1568](https://github.com/DataDog/dd-sdk-android/pull/1568)
* [IMPROVEMENT] Session Replay: Use `internalLogger` in SR modules. See [#1574](https://github.com/DataDog/dd-sdk-android/pull/1574)
* [IMPROVEMENT] Add the `additionalProperties` capability to telemetry debug log event. See [#1575](https://github.com/DataDog/dd-sdk-android/pull/1575)
* [IMPROVEMENT] Global: Collect the `batch_deleted` telemetry. See [#1577](https://github.com/DataDog/dd-sdk-android/pull/1577)
* [IMPROVEMENT] RUM: Fix view tracking gap. See [#1578](https://github.com/DataDog/dd-sdk-android/pull/1578)
* [IMPROVEMENT] Fix tests around `InternalLogger`. See [#1579](https://github.com/DataDog/dd-sdk-android/pull/1579)
* [IMPROVEMENT] Introduce the new `InternalLogger#metric` API. See [#1581](https://github.com/DataDog/dd-sdk-android/pull/1581)
* [IMPROVEMENT] Global: Collect the `batch_closed` telemetry. See [#1586](https://github.com/DataDog/dd-sdk-android/pull/1586)
* [IMPROVEMENT] Add multiple instance sample. See [#1587](https://github.com/DataDog/dd-sdk-android/pull/1587)
* [IMPROVEMENT] Global: Provide the `inBackground` property for `batch_delete` metric. See [#1588](https://github.com/DataDog/dd-sdk-android/pull/1588)
* [IMPROVEMENT] Global: Unregister process lifecycle monitor in core instance stop. See [#1589](https://github.com/DataDog/dd-sdk-android/pull/1589)
* [IMPROVEMENT] Session Replay: Add SR integration tests for `TextView` and `EditText` view type. See [#1593](https://github.com/DataDog/dd-sdk-android/pull/1593)
* [MAINTENANCE] Mention Datadog SDK explicitly in dogfood script. See [#1557](https://github.com/DataDog/dd-sdk-android/pull/1557)
* [MAINTENANCE] Remove redundant `sqlite` product flavour folder in the sample app. See [#1559](https://github.com/DataDog/dd-sdk-android/pull/1559)
* [MAINTENANCE] Next dev cycle 2.1.0. See [#1562](https://github.com/DataDog/dd-sdk-android/pull/1562)
* [MAINTENANCE] Remove the bridge dogfooding step, bridge repo is archived. See [#1571](https://github.com/DataDog/dd-sdk-android/pull/1571)
* [MAINTENANCE] Update gitlab CI env variables for gitlab 16. See [#1595](https://github.com/DataDog/dd-sdk-android/pull/1595)
* [DOCS] Session Replay: Add the `README` files for SR modules. See [#1567](https://github.com/DataDog/dd-sdk-android/pull/1567)

# 2.0.0 / 2023-07-31

This is the first official production version of SDK v2 containing  the new architecture for features initialisation and dependencies distribution. See the [migration guide](https://github.com/DataDog/dd-sdk-android/blob/62aac79c3c68c4da02c96ab1071fb5e63f1b8b89/MIGRATION.MD) for details.

Below you can find the change logs in comparison with out last stable version `1.19.3`:

* [FEATURE] RUM: Introduce Mobile Session Replay (in Beta).
* [IMPROVEMENT] RUM: Remove tracking of view loading time and fix unit tests. See [#1545](https://github.com/DataDog/dd-sdk-android/pull/1545)
* [IMPROVEMENT] Don't report OkHttp throwables to telemetry. See [#1548](https://github.com/DataDog/dd-sdk-android/pull/1548)
* [IMPROVEMENT] Use `implementation` dependency for features in integrations modules. See [#1552](https://github.com/DataDog/dd-sdk-android/pull/1552)
* [IMPROVEMENT] Remove `dd-sdk-android-ktx` module. See [#1555](https://github.com/DataDog/dd-sdk-android/pull/1555)
* [BUGFIX] RUM: Fix memory leak in `JankStats` usage. See [#1553](https://github.com/DataDog/dd-sdk-android/pull/1553)
* [DOCS] Remove redundant docs. See [#1540](https://github.com/DataDog/dd-sdk-android/pull/1540)
* [DOCS] Update documentation for SDK v2. See [#1549](https://github.com/DataDog/dd-sdk-android/pull/1549)
* [IMPROVEMENT] Global: Provide the `uploadFrequency` per feature. See [#1533](https://github.com/DataDog/dd-sdk-android/pull/1533)
* [IMPROVEMENT] RUM: Update documentation of `ViewEventMapper`. See [#1537](https://github.com/DataDog/dd-sdk-android/pull/1537)
* [IMPROVEMENT] RUM: Support `navHost` hosted by `FragmentContainerView` for `NavigationViewTrackingStrategy`. See [#1538](https://github.com/DataDog/dd-sdk-android/pull/1538)
* [IMPROVEMENT] Session Replay: Always listen and react to RUM session state. See [#1539](https://github.com/DataDog/dd-sdk-android/pull/1539)
* [IMPROVEMENT] Session Replay: Remove explicit dependency on RUM module from Session Replay. See [#1541](https://github.com/DataDog/dd-sdk-android/pull/1541)
* [IMPROVEMENT] Update Compose Navigation version to 2.6.0. See [#1542](https://github.com/DataDog/dd-sdk-android/pull/1542)
* [IMPROVEMENT] Don't reexport `OkHttp` from `Glide`. See [#1543](https://github.com/DataDog/dd-sdk-android/pull/1543)
* [IMPROVEMENT] Introduce known file cache and cleanup throttling in `BatchFileOrchestrator` in order to reduce the number of syscalls. See [#1506](https://github.com/DataDog/dd-sdk-android/pull/1506)
* [IMPROVEMENT] Logs: Alter public API of `Logger` to receive `Any` data type. See [#1324](https://github.com/DataDog/dd-sdk-android/pull/1324)
* [IMPROVEMENT] RUM: Use `JankStats` for FPS measuring. See [#1405](https://github.com/DataDog/dd-sdk-android/pull/1405)
* [IMPROVEMENT] RUM: Fix `JankStats` usage. See [#1512](https://github.com/DataDog/dd-sdk-android/pull/1512)
* [BUGFIX] RUM: Keep old `viewId`s for view scope. See [#1448](https://github.com/DataDog/dd-sdk-android/pull/1448)

# 2.0.0-beta3 / 2023-07-26

This is a beta release of SDK v2. Compared to SDK v1 it contains breaking changes related to the SDK setup and APIs. See the [migration guide](https://github.com/DataDog/dd-sdk-android/blob/5c9feb900856a6d7b3623820dade1eaead1498b9/CHANGELOG.md) for details.

Changes in comparison with `2.0.0-beta2`:

* [IMPROVEMENT] RUM: Remove tracking of view loading time and fix unit tests. See [#1545](https://github.com/DataDog/dd-sdk-android/pull/1545)
* [IMPROVEMENT] Don't report OkHttp throwables to telemetry. See [#1548](https://github.com/DataDog/dd-sdk-android/pull/1548)
* [IMPROVEMENT] Use `implementation` dependency for features in integrations modules. See [#1552](https://github.com/DataDog/dd-sdk-android/pull/1552)
* [IMPROVEMENT] Remove `dd-sdk-android-ktx` module. See [#1555](https://github.com/DataDog/dd-sdk-android/pull/1555)
* [BUGFIX] RUM: Fix memory leak in `JankStats` usage. See [#1553](https://github.com/DataDog/dd-sdk-android/pull/1553)
* [DOCS] Remove redundant docs. See [#1540](https://github.com/DataDog/dd-sdk-android/pull/1540)
* [DOCS] Update documentation for SDK v2. See [#1549](https://github.com/DataDog/dd-sdk-android/pull/1549)

# 2.0.0-beta2 / 2023-07-17

This is a beta release of SDK v2. Compared to SDK v1 it contains breaking changes related to the SDK setup and APIs. See the [migration guide](https://github.com/DataDog/dd-sdk-android/blob/8d1f9abb101039abcd44ffed2823655c33e5129f/MIGRATION.MD) for details.

Changes in comparison with `2.0.0-beta1`:

* [IMPROVEMENT] Global: Provide the `uploadFrequency` per feature. See [#1533](https://github.com/DataDog/dd-sdk-android/pull/1533)
* [IMPROVEMENT] RUM: Update documentation of `ViewEventMapper`. See [#1537](https://github.com/DataDog/dd-sdk-android/pull/1537)
* [IMPROVEMENT] RUM: Support `navHost` hosted by `FragmentContainerView` for `NavigationViewTrackingStrategy`. See [#1538](https://github.com/DataDog/dd-sdk-android/pull/1538)
* [IMPROVEMENT] Session Replay: Always listen and react to RUM session state. See [#1539](https://github.com/DataDog/dd-sdk-android/pull/1539)
* [IMPROVEMENT] Session Replay: Remove explicit dependency on RUM module from Session Replay. See [#1541](https://github.com/DataDog/dd-sdk-android/pull/1541)
* [IMPROVEMENT] Update Compose Navigation version to 2.6.0. See [#1542](https://github.com/DataDog/dd-sdk-android/pull/1542)
* [IMPROVEMENT] Don't reexport `OkHttp` from `Glide`. See [#1543](https://github.com/DataDog/dd-sdk-android/pull/1543)

# 1.19.3 / 2023-07-11

* [IMPROVEMENT] RUM: Introduce known file cache and cleanup throttling in `BatchFileOrchestrator` in order to reduce the number of syscalls. See [#1506](https://github.com/DataDog/dd-sdk-android/pull/1506)

# 2.0.0-beta1 / 2023-07-07

This is the first release of SDK v2. It contains breaking changes related to the SDK setup and APIs. See the [migration guide](https://github.com/DataDog/dd-sdk-android/blob/026fc30f5c28226b244a0f6884841cbcac9c864b/MIGRATION.MD) for details.

Functional changes in comparison with `1.19.2`:

* [IMPROVEMENT] Introduce known file cache and cleanup throttling in `BatchFileOrchestrator` in order to reduce the number of syscalls. See [#1506](https://github.com/DataDog/dd-sdk-android/pull/1506)
* [IMPROVEMENT] Logs: Alter public API of `Logger` to receive `Any` data type. See [#1324](https://github.com/DataDog/dd-sdk-android/pull/1324)
* [IMPROVEMENT] RUM: Use `JankStats` for FPS measuring. See [#1405](https://github.com/DataDog/dd-sdk-android/pull/1405)
* [IMPROVEMENT] RUM: Fix `JankStats` usage. See [#1512](https://github.com/DataDog/dd-sdk-android/pull/1512)
* [BUGFIX] RUM: Keep old `viewId`s for view scope. See [#1448](https://github.com/DataDog/dd-sdk-android/pull/1448)

# 1.19.2 / 2023-06-05

* [REVERT] RUM: Force new session at SDK initialization. See [#1399](https://github.com/DataDog/dd-sdk-android/pull/1399)

# 1.19.1 / 2023-05-30

* [IMPROVEMENT] RUM: Force new session at SDK initialization. See [#1399](https://github.com/DataDog/dd-sdk-android/pull/1399)
* [BUGFIX] RUM: Ignore adding custom timings and feature flags for the stopped view. See [#1433](https://github.com/DataDog/dd-sdk-android/pull/1433)

# 1.19.0 / 2023-04-24

* [FEATURE] RUM: Allow users to stop a RUM session. See [#1356](https://github.com/DataDog/dd-sdk-android/pull/1356)
* [FEATURE] APM: Add tracer sampling rate. See [#1393](https://github.com/DataDog/dd-sdk-android/pull/1393)
* [IMPROVEMENT] Create a minimal WearOS sample to test compatibility. See [#1384](https://github.com/DataDog/dd-sdk-android/pull/1384)
* [BUGFIX] RUM: Fix stopped `RUMViewManager` from being able to start new views. See [#1381](https://github.com/DataDog/dd-sdk-android/pull/1381)
* [MAINTENANCE] Update RUM Event Schema. See [#1383](https://github.com/DataDog/dd-sdk-android/pull/1383)
* [DOCS] Delete referenced docs and update README. See [#1376](https://github.com/DataDog/dd-sdk-android/pull/1376)

# 1.18.1 / 2023-03-30

* [IMPROVEMENT] RUM: Remove extra telemetry sent when detecting refresh rate scale. See [#1358](https://github.com/DataDog/dd-sdk-android/pull/1358)

# 1.18.0 / 2023-03-21

* [FEATURE] RUM: Add `addFeatureFlagEvaluation` function for RUM. See [#1265](https://github.com/DataDog/dd-sdk-android/pull/1265)
* [FEATURE] RUM: Implement webview proxy for cross platform. See [#1290](https://github.com/DataDog/dd-sdk-android/pull/1290)
* [IMPROVEMENT] RUM: Add support to `AP1`. See [#1268](https://github.com/DataDog/dd-sdk-android/pull/1268)
* [IMPROVEMENT] RUM `ApplicationLaunch` logic changes. See [#1278](https://github.com/DataDog/dd-sdk-android/pull/1278)
* [IMPROVEMENT] RUM: Add internal telemetry configuration sampling rate. See [#1310](https://github.com/DataDog/dd-sdk-android/pull/1310)
* [BUGFIX] RUM: Prevent reporting invalid cpu ticks per seconds. See [#1308](https://github.com/DataDog/dd-sdk-android/pull/1308)
* [BUGFIX] RUM: Fix timing of `ApplicationLaunch` view and `application_start` events. See [#1305](https://github.com/DataDog/dd-sdk-android/pull/1305)
* [BUGFIX] RUM: Fix telemetry sampling internal configuration for Flutter. See [#1326](https://github.com/DataDog/dd-sdk-android/pull/1326)
* [MAINTENANCE] Make tests more accurate with url case sensitivity. See [#1263](https://github.com/DataDog/dd-sdk-android/pull/1263)
* [MAINTENANCE] Update E2E tests with valid resource (as to not get 404). See [#1266](https://github.com/DataDog/dd-sdk-android/pull/1266)
* [MAINTENANCE] Update RUM Telemetry event schema to latest. See [#1319](https://github.com/DataDog/dd-sdk-android/pull/1319)
* [MAINTENANCE] Stabilize integration tests. See [#1329](https://github.com/DataDog/dd-sdk-android/pull/1329)
* [MAINTENANCE] Update static analysis dependency. See [#1342](https://github.com/DataDog/dd-sdk-android/pull/1342)
* [DOCS] Fix numbering in Android tracing instructions markdown. See [#1227](https://github.com/DataDog/dd-sdk-android/pull/1227)
* [DOCS] Updated privacy controls in application setup. See [#1281](https://github.com/DataDog/dd-sdk-android/pull/1281)
* [DOCS] Add app variant definition in docs. See [#1296](https://github.com/DataDog/dd-sdk-android/pull/1296)
* [DOCS] Add link explaining how to stop collecting geolocation data. See [#1327](https://github.com/DataDog/dd-sdk-android/pull/1327)
* [DOCS] Note about how to stop collecting geolocation data. See [#1328](https://github.com/DataDog/dd-sdk-android/pull/1328)

# 1.17.2 / 2023-03-06

* [BUGFIX] Global: Handle devices not reported properly their power source. See [#1315](https://github.com/DataDog/dd-sdk-android/pull/1315)
* [BUGFIX] RUM: Detect device's refresh rate with NavigationViewTrackingStrategy. See [#1312](https://github.com/DataDog/dd-sdk-android/pull/1312)

# 1.17.1 / 2023-02-20

* [BUGFIX] RUM: Revert: Detect device's refresh rate from vital monitor. See [#1251](https://github.com/DataDog/dd-sdk-android/pull/1251)
* [BUGFIX] RUM: The `RumEventMapper` checks `ViewEvent`s by reference. See [#1279](https://github.com/DataDog/dd-sdk-android/pull/1279)
* [BUGFIX] Global: Remove `okhttp3.internal` package usage. See [#1288](https://github.com/DataDog/dd-sdk-android/pull/1288)

# 1.17.0 / 2023-01-30

* [FEATURE] Tracing: Allow the usage of OTel headers in distributed tracing. See [#1229](https://github.com/DataDog/dd-sdk-android/pull/1229)
* [IMPROVEMENT] RUM: Remove cross platform duplicates crashes. See [#1215](https://github.com/DataDog/dd-sdk-android/pull/1215)
* [IMPROVEMENT] RUM: Prevent reporting ANR when app is in background. See [#1239](https://github.com/DataDog/dd-sdk-android/pull/1239)
* [BUGFIX] Use lazy initialization of network-related properties in SampleApplication to be able to pick global first party hosts. See [#1218](https://github.com/DataDog/dd-sdk-android/pull/1218)
* [BUGFIX] Detect device's refresh rate from vital monitor. See [#1251](https://github.com/DataDog/dd-sdk-android/pull/1251)
* [SDK v2] Remove unused `PayloadFormat` and `SdkEndpoint` classes from SDK v2 APIs. See [#1161](https://github.com/DataDog/dd-sdk-android/pull/1161)
* [SDK v2] Make a local copy of tags before creating `LogEvent`. See [#1171](https://github.com/DataDog/dd-sdk-android/pull/1171)
* [SDK v2] Remove duplication of `UserInfo` and `NetworkInfo` classes. See [#1170](https://github.com/DataDog/dd-sdk-android/pull/1170)
* [SDK v2] Use message bus to report Java crashes to RUM. See [#1173](https://github.com/DataDog/dd-sdk-android/pull/1173)
* [SDK v2] Add the `forceNewBatch` option into the `FeatureScope`. See [#1174](https://github.com/DataDog/dd-sdk-android/pull/1174)
* [SDK v2] Use message bus to report NDK crashes to RUM. See [#1177](https://github.com/DataDog/dd-sdk-android/pull/1177)
* [SDK v2] Remove site property from `DatadogContext`. See [#1181](https://github.com/DataDog/dd-sdk-android/pull/1181)
* [SDK v2] Delete obsolete feature-specific uploaders. See [#1199](https://github.com/DataDog/dd-sdk-android/pull/1199)
* [SDK v2] Use `InternalLogger`. See [#1200](https://github.com/DataDog/dd-sdk-android/pull/1200)
* [SDK v2] Remove `Companion` objects with non-public member from Public API. See [#1207](https://github.com/DataDog/dd-sdk-android/pull/1207)
* [SDK v2] Send logs for `Span` using message bus. See [#1211](https://github.com/DataDog/dd-sdk-android/pull/1211)
* [SDK v2] `RequestFactory` can throw exceptions. See [#1214](https://github.com/DataDog/dd-sdk-android/pull/1214)
* [SESSION REPLAY] Fix NPE in `ScreenRecorder` when wrapping a null `window.callback`. See [#1164](https://github.com/DataDog/dd-sdk-android/pull/1164)
* [SESSION REPLAY] Handle SR requests through `SessionReplayRequestsFactory`. See [#1176](https://github.com/DataDog/dd-sdk-android/pull/1176)
* [SESSION REPLAY] Provide SR touch data following the new proposed format. See [#1187](https://github.com/DataDog/dd-sdk-android/pull/1187)
* [SESSION REPLAY] Drop the covered wireframes only if top ones have a background. See [#1198](https://github.com/DataDog/dd-sdk-android/pull/1198)
* [SESSION REPLAY] Fix flaky `WireframeUtils` test. See [#1208](https://github.com/DataDog/dd-sdk-android/pull/1208)
* [SESSION REPLAY] Add dialogs recording support. See [#1206](https://github.com/DataDog/dd-sdk-android/pull/1206)
* [SESSION REPLAY] `RequestFactory` allow to throw exceptions. See [#1217](https://github.com/DataDog/dd-sdk-android/pull/1217)
* [SESSION REPLAY] Use case insensitive when checking wireframe background color opacity. See [#1223](https://github.com/DataDog/dd-sdk-android/pull/1223)
* [SESSION REPLAY] Resolve view snapshot background from theme color. See [#1230](https://github.com/DataDog/dd-sdk-android/pull/1230)
* [MAINTENANCE] Merge `develop` branch into SDK v2 branch. See [#1168](https://github.com/DataDog/dd-sdk-android/pull/1168)
* [MAINTENANCE] Next dev version 1.17.0. See [#1190](https://github.com/DataDog/dd-sdk-android/pull/1190)
* [MAINTENANCE] Upgrade detekt pipeline version. See [#1192](https://github.com/DataDog/dd-sdk-android/pull/1192)
* [MAINTENANCE] Update `apiSurface`. See [#1193](https://github.com/DataDog/dd-sdk-android/pull/1193)
* [MAINTENANCE] Fix flaky `rum_rummonitor_add_background_custom_action_with_outcome` nightly test. See [#1195](https://github.com/DataDog/dd-sdk-android/pull/1195)
* [MAINTENANCE] Merge `release/1.16.0` branch into develop branch. See [#1194](https://github.com/DataDog/dd-sdk-android/pull/1194)
* [MAINTENANCE] Merge `develop` branch into SDK v2 branch. See [#1196](https://github.com/DataDog/dd-sdk-android/pull/1196)
* [MAINTENANCE] Fix flaky telemetry test. See [#1197](https://github.com/DataDog/dd-sdk-android/pull/1197)
* [MAINTENANCE] Use latest version of shared pipeline. See [#1201](https://github.com/DataDog/dd-sdk-android/pull/1201)
* [MAINTENANCE] Enable CodeQL analysis. See [#1204](https://github.com/DataDog/dd-sdk-android/pull/1204)
* [MAINTENANCE] Speed up build of sample app for CodeQL scan. See [#1221](https://github.com/DataDog/dd-sdk-android/pull/1221)
* [MAINTENANCE] Merge release 1.16.0 branch into develop. See [#1228](https://github.com/DataDog/dd-sdk-android/pull/1228)
* [MAINTENANCE] Merge develop into SDK v2 branch. See [#1224](https://github.com/DataDog/dd-sdk-android/pull/1224)
* [MAINTENANCE] Gradle 7.6 & AGP 7.4.0. See [#1232](https://github.com/DataDog/dd-sdk-android/pull/1232)
* [MAINTENANCE] Merge SDK v2 branch into develop branch. See [#1237](https://github.com/DataDog/dd-sdk-android/pull/1237)
* [MAINTENANCE] Use custom detekt rule. See [#1233](https://github.com/DataDog/dd-sdk-android/pull/1233)

# 1.16.0 / 2023-01-10

* [BUGFIX] Global: Use safe context for directBootAware host apps. See [#1209](https://github.com/DataDog/dd-sdk-android/pull/1209)
* [BUGFIX] Global: Provide frozen snapshot of features context when requested. See [#1213](https://github.com/DataDog/dd-sdk-android/pull/1213)
* [IMPROVEMENT] Tracing: Tracing feature stores context in the common context storage. See [#1216](https://github.com/DataDog/dd-sdk-android/pull/1216)
* [IMPROVEMENT] Telemetry: Apply extra sampling rate to the configuration telemetry. See [#1222](https://github.com/DataDog/dd-sdk-android/pull/1222)

# 1.16.0-beta1 / 2022-12-13

* [FEATURE] Global: Unlock encryption API for SDK v2. See [#935](https://github.com/DataDog/dd-sdk-android/pull/935)
* [FEATURE] RUM: Add telemetry configuration mapper. See [#1142](https://github.com/DataDog/dd-sdk-android/pull/1142)
* [FEATURE] Logs: Add a logger method to log error information from strings. See [#1143](https://github.com/DataDog/dd-sdk-android/pull/1143)
* [IMPROVEMENT] Global: Ensure thread safety. See [#936](https://github.com/DataDog/dd-sdk-android/pull/936)
* [IMPROVEMENT] RUM: Use RumFeature#context instead of CoreFeature#contextRef in RumFeature. See [#982](https://github.com/DataDog/dd-sdk-android/pull/982)
* [IMPROVEMENT] Global: Observe uncaught exception in executors. See [#1125](https://github.com/DataDog/dd-sdk-android/pull/1125)
* [IMPROVEMENT] RUM: Update default values for RUM events. See [#1139](https://github.com/DataDog/dd-sdk-android/pull/1139)
* [IMPROVEMENT] Logs: Add device.architecture to logs. See [#1140](https://github.com/DataDog/dd-sdk-android/pull/1140)
* [BUGFIX] Logs: Make a local copy of tags before creating LogEvent. See [#1172](https://github.com/DataDog/dd-sdk-android/pull/1172)
* [BUGFIX] RUM: Synchronize access to DatadogRumMonitor#rootScope when processing fatal error. See [#1186](https://github.com/DataDog/dd-sdk-android/pull/1186)
* [DOCS] Small Link Nit. See [#1028](https://github.com/DataDog/dd-sdk-android/pull/1028)
* [DOCS] Android Data Collected Edits. See [#1059](https://github.com/DataDog/dd-sdk-android/pull/1059)
* [DOCS] Fix sample in README. See [#1141](https://github.com/DataDog/dd-sdk-android/pull/1141)
* [DOCS] Fix sample code in addAction API. See [#1046](https://github.com/DataDog/dd-sdk-android/pull/1046)
* [DOCS] Fix link to setup facets and measures. See [#1179](https://github.com/DataDog/dd-sdk-android/pull/1179)
* [DOCS] Fix typo in CONTRIBUTING.md. See [#1188](https://github.com/DataDog/dd-sdk-android/pull/1188)
* [SDK v2] Datadog singleton. See [#918](https://github.com/DataDog/dd-sdk-android/pull/918)
* [SDK v2] Make SDK Features simple classes. See [#928](https://github.com/DataDog/dd-sdk-android/pull/928)
* [SDK v2] Use TLV format for data storage. See [#931](https://github.com/DataDog/dd-sdk-android/pull/931)
* [SDK v2] Single Storage. See [#932](https://github.com/DataDog/dd-sdk-android/pull/932)
* [SDK v2] Create Feature configuration interfaces. See [#933](https://github.com/DataDog/dd-sdk-android/pull/933)
* [SDK v2] Write batch metadata. See [#943](https://github.com/DataDog/dd-sdk-android/pull/943)
* [SDK v2] Rework file persistence layer. See [#947](https://github.com/DataDog/dd-sdk-android/pull/947)
* [SDK v2] SDK v2 upload pipeline. See [#956](https://github.com/DataDog/dd-sdk-android/pull/956)
* [SDK v2] Data is written in the SDK specific location. See [#975](https://github.com/DataDog/dd-sdk-android/pull/975)
* [SDK v2] Bring tests back. See [#977](https://github.com/DataDog/dd-sdk-android/pull/977)
* [SDK v2] Provide core SDK context. See [#988](https://github.com/DataDog/dd-sdk-android/pull/988)
* [SDK v2] Improvement to the reading batch logic. See [#992](https://github.com/DataDog/dd-sdk-android/pull/992)
* [SDK v2] Features can store their context in SDK context. See [#1036](https://github.com/DataDog/dd-sdk-android/pull/1036)
* [SDK v2] Use SDK v2 components in the upload pipeline. See [#1040](https://github.com/DataDog/dd-sdk-android/pull/1040)
* [SDK v2] Switch to the SDK v2 storage component. See [#1051](https://github.com/DataDog/dd-sdk-android/pull/1051)
* [SDK v2] Update DatadogCore initialization tests. See [#1056](https://github.com/DataDog/dd-sdk-android/pull/1056)
* [SDK v2] Register V1 features as V2. See [#1069](https://github.com/DataDog/dd-sdk-android/pull/1069)
* [SDK v2] Create storage and uploader outside of the feature. See [#1070](https://github.com/DataDog/dd-sdk-android/pull/1070)
* [SDK v2] Remove DataReader v1 usages. See [#1071](https://github.com/DataDog/dd-sdk-android/pull/1071)
* [SDK v2] Use SDK v2 configuration interfaces for features. See [#1079](https://github.com/DataDog/dd-sdk-android/pull/1079)
* [SDK v2] Simple message bus for cross-feature communication. See [#1087](https://github.com/DataDog/dd-sdk-android/pull/1087)
* [SDK v2] Make Storage#writeCurrentBatch async. See [#1094](https://github.com/DataDog/dd-sdk-android/pull/1094)
* [SDK v2] Use event write context for Logs. See [#1103](https://github.com/DataDog/dd-sdk-android/pull/1103)
* [SDK v2] Use event write context for Traces. See [#1106](https://github.com/DataDog/dd-sdk-android/pull/1106)
* [SDK v2] Use event write context for Session Replay. See [#1107](https://github.com/DataDog/dd-sdk-android/pull/1107)
* [SDK v2] Use event write context in RUM. See [#1117](https://github.com/DataDog/dd-sdk-android/pull/1117)
* [SDK v2] Remove RumEventSourceProvider. See [#1119](https://github.com/DataDog/dd-sdk-android/pull/1119)
* [SDK v2] Use event write context in WebView Logs. See [#1121](https://github.com/DataDog/dd-sdk-android/pull/1121)
* [SDK v2] Make implementations of EventBatchWriter return result instead of using listener. See [#1097](https://github.com/DataDog/dd-sdk-android/pull/1097)
* [SDK v2] Avoid capturing shared state by Event Write Context. See [#1126](https://github.com/DataDog/dd-sdk-android/pull/1126)
* [SDK v2] Move global RUM context into generic feature context storage. See [#1146](https://github.com/DataDog/dd-sdk-android/pull/1146)
* [SDK v2] Improve SDK performance a bit. See [#1153](https://github.com/DataDog/dd-sdk-android/pull/1153)
* [SDK v2] Create implementation of InternalLogger. See [#1155](https://github.com/DataDog/dd-sdk-android/pull/1155)
* [SDK v2] Fix devLogger. See [#1156](https://github.com/DataDog/dd-sdk-android/pull/1156)
* [SDK v2] Prepare merge of SDK v2 branch into develop branch. See [#1158](https://github.com/DataDog/dd-sdk-android/pull/1158)
* [SDK v2] Fix flaky TLV format reader test. See [#1166](https://github.com/DataDog/dd-sdk-android/pull/1166)
* [SDK v2] Improve batch upload wait timeout handling. See [#1182](https://github.com/DataDog/dd-sdk-android/pull/1182)
* [SDK v2] Fix possible crash during the telemetry processing. See [#1184](https://github.com/DataDog/dd-sdk-android/pull/1184)
* [SESSION REPLAY] Setup dd-sdk-android-session-replay module. See [#953](https://github.com/DataDog/dd-sdk-android/pull/953)
* [SESSION REPLAY] Add Session Replay Public API. See [#974](https://github.com/DataDog/dd-sdk-android/pull/974)
* [SESSION REPLAY] Add Session Replay Public API tests. See [#985](https://github.com/DataDog/dd-sdk-android/pull/985)
* [SESSION REPLAY] Generate Session Replay modes based on the JSON schemas. See [#986](https://github.com/DataDog/dd-sdk-android/pull/986)
* [SESSION REPLAY] Add Session Replay lifecycle callbacks. See [#993](https://github.com/DataDog/dd-sdk-android/pull/993)
* [SESSION REPLAY] Adapt PokoGenerator and Session Replay models generator to new schemas. See [#1002](https://github.com/DataDog/dd-sdk-android/pull/1002)
* [SESSION REPLAY] Add session replay recorder basic logic. See [#1007](https://github.com/DataDog/dd-sdk-android/pull/1007)
* [SESSION REPLAY] Intercept window touch events as Session Replay TouchData. See [#1009](https://github.com/DataDog/dd-sdk-android/pull/1009)
* [SESSION REPLAY] Processor - process FullSnapshotRecords and dispatch to persister. See [#1013](https://github.com/DataDog/dd-sdk-android/pull/1013)
* [SESSION REPLAY] Process Session Replay touch and orientation change events. See [#1014](https://github.com/DataDog/dd-sdk-android/pull/1014)
* [SESSION REPLAY] SnapshotProcessor - process wireframe mutations. See [#1033](https://github.com/DataDog/dd-sdk-android/pull/1033)
* [SESSION REPLAY] Add the Session Replay MASK_ALL/ALLOW_ALL privacy strategies. See [#1035](https://github.com/DataDog/dd-sdk-android/pull/1035)
* [SESSION REPLAY] Use View hashcode as unique identifier for mapped Wireframe. See [#1037](https://github.com/DataDog/dd-sdk-android/pull/1037)
* [SESSION REPLAY] SnapshotProcessor - do not write anything if there was no mutation detected. See [#1038](https://github.com/DataDog/dd-sdk-android/pull/1038)
* [SESSION REPLAY] Create the Session Replay Writer component. See [#1041](https://github.com/DataDog/dd-sdk-android/pull/1041)
* [SESSION REPLAY] Send hasReplay property for RUM events. See [#1054](https://github.com/DataDog/dd-sdk-android/pull/1054)
* [SESSION REPLAY] Add Session Replay Uploader. See [#1063](https://github.com/DataDog/dd-sdk-android/pull/1063)
* [SESSION REPLAY] Fix the mutation resolver alg based on the Heckels definition. See [#1065](https://github.com/DataDog/dd-sdk-android/pull/1065)
* [SESSION REPLAY] Use RUM timestamp offset when resolving Session Replay timestamp. See [#1068](https://github.com/DataDog/dd-sdk-android/pull/1068)
* [SESSION REPLAY] Correct the way we handle orientation change events in SR. See [#1073](https://github.com/DataDog/dd-sdk-android/pull/1073)
* [SESSION REPLAY] Optimize BaseWireframeMapper#colorAndAlphaAsStringHexa function. See [#1077](https://github.com/DataDog/dd-sdk-android/pull/1077)
* [SESSION REPLAY] Accept optional Typeface when resolving fontStyle. See [#1082](https://github.com/DataDog/dd-sdk-android/pull/1082)
* [SESSION REPLAY] Skip new lines and spaces when obfuscating texts. See [#1078](https://github.com/DataDog/dd-sdk-android/pull/1078)
* [SESSION REPLAY] Correctly handle clipped elements and scrollable lists. See [#1101](https://github.com/DataDog/dd-sdk-android/pull/1101)
* [SESSION REPLAY] Flush buffered motion event positions periodically. See [#1108](https://github.com/DataDog/dd-sdk-android/pull/1108)
* [SESSION REPLAY] Fix source key in SR upload form. See [#1109](https://github.com/DataDog/dd-sdk-android/pull/1109)
* [SESSION REPLAY] Increase the screen snapshot frequency. See [#1110](https://github.com/DataDog/dd-sdk-android/pull/1110)
* [SESSION REPLAY] Start/Stop Session recording based on the RUM session state. See [#1122](https://github.com/DataDog/dd-sdk-android/pull/1122)
* [SESSION REPLAY] Sync SR touch and screen recorders. See [#1150](https://github.com/DataDog/dd-sdk-android/pull/1150)
* [SESSION REPLAY] Correctly resolve ShapeStyle opacity in Wireframes. See [#1152](https://github.com/DataDog/dd-sdk-android/pull/1152)
* [MAINTENANCE] Merge develop into sdkv2 branch. See [#978](https://github.com/DataDog/dd-sdk-android/pull/978)
* [MAINTENANCE] Add support to Json Schema's oneOf syntax. See [#976](https://github.com/DataDog/dd-sdk-android/pull/976)
* [MAINTENANCE] Small fix in the PokoGenerator tool. See [#989](https://github.com/DataDog/dd-sdk-android/pull/989)
* [MAINTENANCE] Use Gradle lazy API more. See [#996](https://github.com/DataDog/dd-sdk-android/pull/996)
* [MAINTENANCE] Gradle 7.5. See [#997](https://github.com/DataDog/dd-sdk-android/pull/997)
* [MAINTENANCE] Merge develop branch into SDK v2 branch. See [#1034](https://github.com/DataDog/dd-sdk-android/pull/1034)
* [MAINTENANCE] Merge 1.14.0 release branch into master branch . See [#1048](https://github.com/DataDog/dd-sdk-android/pull/1048)
* [MAINTENANCE] Merge develop into SDK v2 branch. See [#1052](https://github.com/DataDog/dd-sdk-android/pull/1052)
* [MAINTENANCE] Merge release 1.14.1 into master. See [#1062](https://github.com/DataDog/dd-sdk-android/pull/1062)
* [MAINTENANCE] Merge Session Replay branch into SDK v2 branch. See [#1075](https://github.com/DataDog/dd-sdk-android/pull/1075)
* [MAINTENANCE] Merge develop into SDK v2 branch. See [#1076](https://github.com/DataDog/dd-sdk-android/pull/1076)
* [MAINTENANCE] Apply ktlint 0.45.1 rules. See [#1086](https://github.com/DataDog/dd-sdk-android/pull/1086)
* [MAINTENANCE] Move package from AndroidManifest to namespace plugin config property for Session Replay module. See [#1095](https://github.com/DataDog/dd-sdk-android/pull/1095)
* [MAINTENANCE] Merge develop into SDK v2 branch. See [#1104](https://github.com/DataDog/dd-sdk-android/pull/1104)
* [MAINTENANCE] Fix caching of models generation task. See [#1111](https://github.com/DataDog/dd-sdk-android/pull/1111)
* [MAINTENANCE] Fix WireframeUtils flaky tests. See [#1113](https://github.com/DataDog/dd-sdk-android/pull/1113)
* [MAINTENANCE] Fix flaky tests in WireframeUtilsTest. See [#1128](https://github.com/DataDog/dd-sdk-android/pull/1128)
* [MAINTENANCE] Next dev cycle. See [#1132](https://github.com/DataDog/dd-sdk-android/pull/1132)
* [MAINTENANCE] Suppress the deprecation of Bundle#get in nightly test. See [#1137](https://github.com/DataDog/dd-sdk-android/pull/1137)
* [MAINTENANCE] Remove non-existent function in dogfood script. See [#1136](https://github.com/DataDog/dd-sdk-android/pull/1136)
* [MAINTENANCE] Merge release 1.15.0 into develop. See [#1135](https://github.com/DataDog/dd-sdk-android/pull/1135)
* [MAINTENANCE] Merge release 1.15.0 into master. See [#1134](https://github.com/DataDog/dd-sdk-android/pull/1134)
* [MAINTENANCE] Merge develop branch into SDK v2 branch. See [#1138](https://github.com/DataDog/dd-sdk-android/pull/1138)
* [MAINTENANCE] Suppress the deprecation of Bundle#get in nightly test. See [#1144](https://github.com/DataDog/dd-sdk-android/pull/1144)
* [MAINTENANCE] Update telemetry schema. See [#1145](https://github.com/DataDog/dd-sdk-android/pull/1145)
* [MAINTENANCE] Merge develop into SDK v2 branch. See [#1147](https://github.com/DataDog/dd-sdk-android/pull/1147)
* [MAINTENANCE] Add fromJsonElement method to generated models. See [#1148](https://github.com/DataDog/dd-sdk-android/pull/1148)
* [MAINTENANCE] Fix flakiness in DebouncerTest class. See [#1151](https://github.com/DataDog/dd-sdk-android/pull/1151)
* [MAINTENANCE] Fix unit tests flakiness in MutationResolverTest. See [#1157](https://github.com/DataDog/dd-sdk-android/pull/1157)
* [MAINTENANCE] Merge SDK v2 branch into develop branch. See [#1159](https://github.com/DataDog/dd-sdk-android/pull/1159)
* [MAINTENANCE] Add publish task for the Session Replay module. See [#1162](https://github.com/DataDog/dd-sdk-android/pull/1162)
* [MAINTENANCE] Nightly tests improvements. See [#1163](https://github.com/DataDog/dd-sdk-android/pull/1163)
* [MAINTENANCE] Fix some nightly tests flakiness. See [#1165](https://github.com/DataDog/dd-sdk-android/pull/1165)
* [MAINTENANCE] Update telemetry configuration schema. See [#1175](https://github.com/DataDog/dd-sdk-android/pull/1175)
* [MAINTENANCE] Merge master into develop. See [#1183](https://github.com/DataDog/dd-sdk-android/pull/1183)
* [MAINTENANCE] Apply new detekt configuration. See [#1180](https://github.com/DataDog/dd-sdk-android/pull/1180)
* [MAINTENANCE] Fix flaky BroadcastReceiverSystemInfoProvider test. See [#1185](https://github.com/DataDog/dd-sdk-android/pull/1185)
* [MAINTENANCE] Generated code. See [#1189](https://github.com/DataDog/dd-sdk-android/pull/1189)

# 1.15.0 / 2022-11-09

* [FEATURE] RUM: Add frustration signal 'Error Tap'. See [#1006](https://github.com/DataDog/dd-sdk-android/pull/1006)
* [FEATURE] RUM: Report frustration count on views. See [#1030](https://github.com/DataDog/dd-sdk-android/pull/1030)
* [FEATURE] RUM: Add API to enable/disable tracking of frustration signals. See [#1085](https://github.com/DataDog/dd-sdk-android/pull/1085)
* [FEATURE] RUM: Create internal API for sending technical performance metrics. See [#1083](https://github.com/DataDog/dd-sdk-android/pull/1083)
* [FEATURE] RUM: Configuration Telemetry. See [#1118](https://github.com/DataDog/dd-sdk-android/pull/1118)
* [IMPROVEMENT] Internal: Add internal DNS resolver. See [#991](https://github.com/DataDog/dd-sdk-android/pull/991)
* [IMPROVEMENT] RUM: Support sending CPU architecture as part of device info. See [#1000](https://github.com/DataDog/dd-sdk-android/pull/1000)
* [IMPROVEMENT] Internal: Add checks on intake request headers. See [#1005](https://github.com/DataDog/dd-sdk-android/pull/1005)
* [IMPROVEMENT] RUM: Enable custom application version support. See [#1020](https://github.com/DataDog/dd-sdk-android/pull/1020)
* [IMPROVEMENT] RUM: Add configuration method to disable action tracking. See [#1023](https://github.com/DataDog/dd-sdk-android/pull/1023)
* [IMPROVEMENT] Global: Minor performance optimization during serialization into JSON format. See [#1066](https://github.com/DataDog/dd-sdk-android/pull/1066)
* [IMPROVEMENT] Global: Editable additional attributes. See [#1089](https://github.com/DataDog/dd-sdk-android/pull/1089)
* [IMPROVEMENT] RUM: Add tracing sampling attribute. See [#1092](https://github.com/DataDog/dd-sdk-android/pull/1092)
* [IMPROVEMENT] RUM: Invert frame time to get js refresh rate. See [#1105](https://github.com/DataDog/dd-sdk-android/pull/1105)
* [IMPROVEMENT] Global: Target Android 13. See [#1130](https://github.com/DataDog/dd-sdk-android/pull/1130)
* [BUGFIX] Internal: Fix buttons overlap in the sample app. See [#1004](https://github.com/DataDog/dd-sdk-android/pull/1004)
* [BUGFIX] Global: Prevent crash on peekBody. See [#1080](https://github.com/DataDog/dd-sdk-android/pull/1080)
* [BUGFIX] Shutdown Kronos clock only after executors. See [#1127](https://github.com/DataDog/dd-sdk-android/pull/1127)
* [DOCS] Android and Android TV Monitoring Formatting Edit. See [#966](https://github.com/DataDog/dd-sdk-android/pull/966)
* [DOCS] Update statement about default view tracking strategy in the docs. See [#1003](https://github.com/DataDog/dd-sdk-android/pull/1003)
* [DOCS] Android Monitoring Doc Edit. See [#1010](https://github.com/DataDog/dd-sdk-android/pull/1010)
* [DOCS] Adds Secondary Doc Reviewer. See [#1011](https://github.com/DataDog/dd-sdk-android/pull/1011)
* [DOCS] Replace references to iOS classes with correct Android equivalents. See [#1012](https://github.com/DataDog/dd-sdk-android/pull/1012)
* [DOCS] Add setVitalsUpdateFrequency to the doc. See [#1015](https://github.com/DataDog/dd-sdk-android/pull/1015)
* [DOCS] Android Integrated Libraries Update. See [#1021](https://github.com/DataDog/dd-sdk-android/pull/1021)
* [DOCS] Sync Doc Changes From Master to Develop. See [#1024](https://github.com/DataDog/dd-sdk-android/pull/1024)
* [MAINTENANCE] CI: Use a gitlab template for analysis generated files check. See [#1016](https://github.com/DataDog/dd-sdk-android/pull/1016)
* [MAINTENANCE] Update Cmake to 3.22.1. See [#1032](https://github.com/DataDog/dd-sdk-android/pull/1032)
* [MAINTENANCE] Update CODEOWNERS. See [#1045](https://github.com/DataDog/dd-sdk-android/pull/1045)
* [MAINTENANCE] Update AGP to 7.3.0. See [#1060](https://github.com/DataDog/dd-sdk-android/pull/1060)
* [MAINTENANCE] CI: Use KtLint 0.45.1 and dedicated runner image. See [#1081](https://github.com/DataDog/dd-sdk-android/pull/1081)
* [MAINTENANCE] CI: Migrate ktlint CI job to shared gitlab template. See [#1084](https://github.com/DataDog/dd-sdk-android/pull/1084)
* [MAINTENANCE] Update ktlint to 0.47.1. See [#1091](https://github.com/DataDog/dd-sdk-android/pull/1091)
* [MAINTENANCE] Publish SNAPSHOT builds to sonatype on pushes to develop. See [#1093](https://github.com/DataDog/dd-sdk-android/pull/1093)
* [MAINTENANCE] Add version to top level project for nexusPublishing extension. See [#1096](https://github.com/DataDog/dd-sdk-android/pull/1096)
* [MAINTENANCE] Fix import ordering. See [#1098](https://github.com/DataDog/dd-sdk-android/pull/1098)
* [MAINTENANCE] Deprecate DatadogPlugin class and its usage. See [#1100](https://github.com/DataDog/dd-sdk-android/pull/1100)
* [MAINTENANCE] CI: Update .gitlab-ci.yml to use release image for static-analysis job. See [#1102](https://github.com/DataDog/dd-sdk-android/pull/1102)
* [MAINTENANCE] Suppress DatadogPlugin deprecation for instrumented tests. See [#1114](https://github.com/DataDog/dd-sdk-android/pull/1114)
* [MAINTENANCE] Remove Flutter from Dogfooding scripts. See [#1120](https://github.com/DataDog/dd-sdk-android/pull/1120)
* [MAINTENANCE] Fix flaky ANR detection test. See [#1123](https://github.com/DataDog/dd-sdk-android/pull/1123)
* [MAINTENANCE] Android Gradle Plugin 7.3.1. See [#1124](https://github.com/DataDog/dd-sdk-android/pull/1124)
* [MAINTENANCE] Filter out telemetry in the assertions of instrumented RUM tests. See [#1131](https://github.com/DataDog/dd-sdk-android/pull/1131)

# 1.14.1 / 2022-09-27

* [IMPROVEMENT] Global: Add CPU architecture to the collected device information. See [#1000](https://github.com/DataDog/dd-sdk-android/pull/1000)

# 1.14.0 / 2022-09-20

* [FEATURE] Global: Collect OS and device information instead of relying on User-Agent header. See [#945](https://github.com/DataDog/dd-sdk-android/pull/945)
* [IMPROVEMENT] Logs: Add a possibility to define min log level. See [#920](https://github.com/DataDog/dd-sdk-android/pull/920)
* [IMPROVEMENT] Logs: Add `variant` tag to events. See [#1025](https://github.com/DataDog/dd-sdk-android/pull/1025)
* [IMPROVEMENT] RUM: Add a method to add extra user properties. See [#952](https://github.com/DataDog/dd-sdk-android/pull/952) (Thanks [@JosephRoskopf](https://github.com/JosephRoskopf))
* [IMPROVEMENT] RUM: Allow to configure Vitals collection frequency. See [#926](https://github.com/DataDog/dd-sdk-android/pull/926)
* [IMPROVEMENT] RUM: Improve session management logic. See [#948](https://github.com/DataDog/dd-sdk-android/pull/948)
* [IMPROVEMENT] RUM: Back navigation is reported with `back` type. See [#980](https://github.com/DataDog/dd-sdk-android/pull/980)
* [IMPROVEMENT] RUM: Add a possibility to disable automatic view tracking. See [#981](https://github.com/DataDog/dd-sdk-android/pull/981)
* [IMPROVEMENT] RUM: Add a possibility to disable automatic interactions tracking. See [#1023](https://github.com/DataDog/dd-sdk-android/pull/1023)
* [IMPROVEMENT] Global: Remove deprecated APIs. See [#973](https://github.com/DataDog/dd-sdk-android/pull/973)

# 1.13.0 / 2022-06-27

* [BUGFIX] Core: Prevent a rare race condition in the features folder creation
* [BUGFIX] RUM: Update Global RUM context when view is stopped
* [BUGFIX] RUM: Interactions use the window coordinates, and not the screen ones
* [FEATURE] RUM: Add compatibility with Android TV application (see our dedicated artifact to track TV actions)
* [FEATURE] Global: Provide an internal observability mechanism
* [IMPROVEMENT] Global: improve the local LogCat messages from the SDK
* [IMPROVEMENT] RUM: allow client side sampling for RUM Resource tracing
* [IMPROVEMENT] RUM: Disable Vitals collection when app's in background
* [IMPROVEMENT] RUM: Reduce the size of events sent

# 1.12.0 / 2022-04-11

* [BUGFIX] Core: Internal attributes coming from cross-platform are removed after being read
* [BUGFIX] RUM: Ensure Crash report works even when there're no active view [#849](https://github.com/DataDog/dd-sdk-android/issues/849) (Thanks [@emichaux](https://github.com/emichaux))
* [BUGFIX] RUM: Span created from network requests stop sending the query params in the `resource` attribute
* [BUGFIX] RUM: Ongoing action completes when a new view starts
* [FEATURE] RUM: Allow tracking browser RUM events from Webviews
* [FEATURE] RUM: Add support to Jetpack Compose
* [IMPROVEMENT] Logs: Support adding org.json.JSONObject attributes to Loggers [#588](https://github.com/DataDog/dd-sdk-android/issues/588) (Thanks [@fleficher](https://github.com/fleficher))
* [IMPROVEMENT] RUM: Automatically track Activity Intents
* [IMPROVEMENT] RUM: Collect RUM events during application launch
* [IMPROVEMENT] RUM: Remove RUM Action automatic filtering
* [IMPROVEMENT] Global: Prevent 3rd party/system dependency calls from crashing the host application
* [IMPROVEMENT] Global: Use the cache folder to store batch files

# 1.11.1 / 2022-01-06

* [BUGFIX] RUM: Prevent potential crash when targeting Android SDK 31 [#709](https://github.com/DataDog/dd-sdk-android/issues/709) (Thanks [@mattking-chip](https://github.com/mattking-chip))

# 1.11.0 / 2021-12-07

* [BUGFIX] RUM: Fix Memory Vital downscaled on Android
* [BUGFIX] RUM: Prevent potential crash in DatadogExceptionHandler [#749](https://github.com/DataDog/dd-sdk-android/issues/749) (Thanks [@ribafish](https://github.com/ribafish))
* [BUGFIX] RUM: Prevent potential crash in WindowCallbackWrapper
* [BUGFIX] RUM: Prevent potential crash NdkCrashReportsPlugin
* [BUGFIX] RUM: Ensure all crash information are saved
* [BUGFIX] Global: Prevent crash on init on KitKat devices [#678](https://github.com/DataDog/dd-sdk-android/issues/678) (Thanks [@eduardb](https://github.com/eduardb))
* [FEATURE] RUM: Add new Mobile Vitals attributes
* [FEATURE] RUM: Adds an optional `RumSessionListener` in the `RumMonitor.Builder` to know when a session starts, and what its UUID is
* [FEATURE] Global: Allow user to set a custom proxy for data intake [#592](https://github.com/DataDog/dd-sdk-android/issues/592) (Thanks [@ruXlab](https://github.com/ruXlab))
* [IMPROVEMENT] RUM: Associate Logs and Traces with RUM Action
* [IMPROVEMENT] RUM: Tag RUM Resources as `native` by default (instead of `xhr`)
* [IMPROVEMENT] RUM: Sanitize NDK crash stacktraces
* [IMPROVEMENT] RUM: Enrich RUM Errors with the Throwable's message
* [IMPROVEMENT] Global: Update the intake request for Datadog's API v2
* [IMPROVEMENT] Global: Add support to US5 endpoint
* [IMPROVEMENT] RUM: Prevent Get leaking memory with OkHttp ResponseBody objects
* [IMPROVEMENT] RUM: Add an action predicate to rename the action target

# 1.10.0 / 2021-09-02

* [BUGFIX] Global: Fix crash when using old OkHttp dependency [#658](https://github.com/DataDog/dd-sdk-android/issues/658) (Thanks [@JessicaYeh](https://github.com/VladBytsyuk))
* [BUGFIX] Global: Prevent retrying endlessly data upload when Client Token is invalid
* [BUGFIX] Global: Support using DD Android SDK with Java 11 
* [BUGFIX] Global: Support proper serialization of nested maps for custom attributes
* [BUGFIX] RUM: Ensure all crashes are reported to RUM
* [FEATURE] APM: Add Data Scrubbing for Spans
* [FEATURE] RUM: Detect ANR
* [FEATURE] RUM: Track Memory and CPU usage, as well as views' refresh rate
* [FEATURE] RUM: Track Actions, Resources and Errors when the application is in background (see `Configuration.Builder().trackBackgroundRumEvents(true).build()`)
* [FEATURE] RUM: Add data scrubbing for Long Tasks
* [IMPROVEMENT] RUM: Replace all `other` or `unknown` resources with xhr (ensuring end-to-end trace is enabled)
* [IMPROVEMENT] RUM: Let children events have the proper view.name attribute
* [IMPROVEMENT] RUM: Keep all custom Action even when a previous action is still active
* [IMPROVEMENT] Global: Update available endpoints (and match documentation names: `US1`, `US3`, `US1_FED` and `EU1`)
* [IMPROVEMENT] Global: All user info should be in usr.*

# 1.9.0 / 2021-06-07

* [BUGFIX] APM: Fix network tracing inconsistencies
* [BUGFIX] APM: Fix span  with custom `MESSAGE` field [#522](https://github.com/DataDog/dd-sdk-android/issues/522) (Thanks [@JessicaYeh](https://github.com/JessicaYeh))
* [BUGFIX] Logs: Fix tag name in Timber `DatadogTree` [#483](https://github.com/DataDog/dd-sdk-android/issues/483) (Thanks [@cfa-eric](https://github.com/cfa-eric))
* [BUGFIX] RUM: Ensure View linked events count is correct when events are discarded
* [BUGFIX] RUM: Fix Resource network timings
* [BUGFIX] APM: Fix span logs timestamp conversion
* [FEATURE] RUM: Detect Long Tasks (tasks blocking the main thread)
* [FEATURE] RUM: add a callback to enrich RUM Resources created from OkHttp Requests
* [IMPROVEMENT] RUM: Remove the "Application crash detected" prefix and ensure the message  is kept
* [IMPROVEMENT] RUM: Add warning when a RUM Action is dropped

# 1.8.1 / 2021-03-04

* [BUGFIX] RUM/APM: handle correctly known hosts in global configuration and interceptors [#513](https://github.com/DataDog/dd-sdk-android/issues/513) (Thanks [@erawhctim](https://github.com/erawhctim))

# 1.8.0 / 2021-02-25

* [BUGFIX] Global: handle correctly incorrect domain names in Interceptors' known hosts
* [BUGFIX] RUM: RUM Context was bundled in spans even when RUM was not enabled
* [FEATURE] Global: Allow user to configure the Upload Frequency (see `Configuration.Builder().setUploadFrequency(…).build()`)
* [FEATURE] Global: Allow user to configure the Batch Size (see `Configuration.Builder().setBatchSize(…).build()`)
* [FEATURE] RUM: Customize Views' name
* [FEATURE] RUM: Send NDK Crash related RUM Error
* [FEATURE] RUM: Track custom timings in RUM Views (see `GlobalRum.get().addTiming("<timing_name>")`)
* [FEATURE] RUM: Provide a PII Data Scrubbing feature (see `Configuration.Builder().setRum***EventMapper(…).build()`)
* [FEATURE] RUM: Send NDK Crash related RUM Error
* [IMPROVEMENT] APM: Stop duplicating APM errors as RUM errors
* [IMPROVEMENT] Logs Align the 'error.kind' attribute value with RUM Error 'error.type'
* [IMPROVEMENT] RUM: Get a more accurate Application loading time
* [IMPROVEMENT] RUM: Add a variant tag on RUM events

# 1.7.0 / 2021-01-04

* [BUGFIX] RUM: fix RUM Error timestamps
* [BUGFIX] RUM: calling `GlobalRum.addAttribute()` with a `null` value would make the application crash
* [BUGFIX] RUM: Actions created with type Custom where sometimes dropped
* [FEATURE] Global: Add support for GDPR compliance feature (see `Datadog.setTrackingConsent()`)
* [FEATURE] Global: Allow setting custom user specific attributes (see `Datadog.setUserInfo()`)
* [IMPROVEMENT] Crash Report: Handle SIGABRT signal in the NDKCrashReporter
* [OTHER] Global: Remove deprecated APIs and warn about future deprecations
* [OTHER] Global: Remove all flavors from sample (allowing to get faster build times)

# 1.6.1 / 2020-11-13

* [BUGFIX] Global: Ensure the network status is properly retrieved on startup

# 1.6.0 / 2020-10-31

* [BUGFIX] RUM: Extend continuous RUM action scope if Resources are still active
* [BUGFIX] RUM: Resources are linked with the wrong Action
* [BUGFIX] Global: Validate the env value passed in the DatadogConfig.Builder
* [BUGFIX] RUM: prevent `trackInterations()` from messing with the Application's theme
* [BUGFIX] Global: Remove unnecessary transitive dependencies from library [#396](https://github.com/DataDog/dd-sdk-android/issues/396) (Thanks [@rashadsookram](https://github.com/rashadsookram))
* [BUGFIX] Global: Prevent a crash in CallbackNetworkProvider
* [FEATURE] Global: Provide an RxJava integration (`dd-sdk-android-rx`)
* [FEATURE] Global: Provide a Coil integration (`dd-sdk-android-coil`)
* [FEATURE] Global: Provide a Fresco integration (`dd-sdk-android-coil`)
* [FEATURE] Global: Provide a SQLDelight integration (`dd-sdk-android-sqldelight`)
* [FEATURE] Global: Provide a Kotlin Coroutines/Flow integration (`dd-sdk-android-ktx`)
* [FEATURE] Global: Provide an extension for SQLiteDatabase integration (`dd-sdk-android-ktx`)
* [FEATURE] RUM: Add a utility to track various resource loading (`RumResourceInputStream`)
* [FEATURE] RUM: Add an extensions to track Android resources and assets as RUM Resources (`dd-sdk-android-ktx`)
* [FEATURE] RUM: Add the APM trace information in the RUM Resource
* [FEATURE] RUM: Track spans with error as RUM Error
* [IMPROVEMENT] Global: Delay the upload of data in case of network exceptions
* [IMPROVEMENT] CrashReport: Add application information in crashes

# 1.5.2 / 2020-09-18

* [BUGFIX] Global: Prevent a crash when sending data. See [#377](https://github.com/DataDog/dd-sdk-android/issues/377) (Thanks [@ronak-earnin](https://github.com/ronak-earnin))

# 1.5.1 / 2020-09-03

* [BUGFIX] RUM: Make sure the RUM data is sent in applications obfuscated or shrunk with Proguard/R8

# 1.5.0 / 2020-08-12

* [FEATURE] RUM: Add a RUM tracking feature:
    - Track User sessions
    - Track Activities or Fragments as Views (or use manual tracing)
    - Track Resources (network requests)
    - Track User interactions (Tap, Scroll, Swipe)
    - Track Errors and crashes
* [FEATURE] RUM: Add helper Interceptor to track OkHttp requests
* [FEATURE] RUM: Add helper WebViewClient and WebChromeClient implementations to track WebView resources
* [FEATURE] RUM: Add helper library to track Glide requests and errors
* [FEATURE] CrashReport: Add a helper library to detect C/C++ crashes in Android NDK
* [FEATURE] Global: add a method to clear all local unsent data
* [BUGFIX] APM: Fix clock skew issue in traced requests
* [BUGFIX] Logs: Prevent Logcat noise from our SDK when running Robolectric tests
* [IMPROVEMENT] Global: Enhance the SDK performance and ensure it works properly in a multi-process application
* [OTHER] Global: The DatadogConfig needs a valid environment name (`envName`), applied to all features
* [OTHER] Global: The serviceName by default will use your application's package name
* [OTHER] Global: The logs and spans sent from the sdk can be found with the filter `source:android`

# 1.4.3 / 2020-06-25

* [IMPROVEMENT] Global: The `source` tag on logs and trace now uses `android` instead of ~~`mobile`~~

# 1.4.2 / 2020-06-12

* [BUGFIX] Global: Fix data upload (some payloads could rarely be malformed)

# 1.4.1 / 2020-05-06

* [BUGFIX] Trace: Fix spans intake (some spans could be missing)

# 1.4.0 / 2020-05-05

* [FEATURE] Global: Update the SDK initialization code
* [FEATURE] Global: Add a Kotlin extension module with Kotlin specific integrations
* [FEATURE] APM: Implement OpenTracing specifications
* [FEATURE] APM: Add helper methods to attach an error to a span
* [FEATURE] APM: Add helper Interceptor to trace OkHttp requests
* [FEATURE] Logs: Add sampling option in the Logger
* [IMPROVEMENT] Logs: Make the log operations thread safe
* [BUGFIX] Logs: Fix rare crash on upload requests
* [BUGFIX] Global: Prevent OutOfMemory crash on upload. See [#164](https://github.com/DataDog/dd-sdk-android/issues/164) (Thanks [@alparp27](https://github.com/alparp27))


# 1.3.1 / 2020-04-30

### Changes

* [BUGFIX] Fix ConcurrentModificationException crash in the FileReader class. See [#234](https://github.com/DataDog/dd-sdk-android/issues/234) (Thanks [@alparp27](https://github.com/alparp27))

# 1.3.0 / 2020-03-02

### Changes

* [FEATURE] Logs: Add the caller class name as a tag in the LogcatLogHandler (only when app is in Debug)
* [FEATURE] Logs: Allow adding a `JsonElement` as Attribute
* [FEATURE] CrashReport: Let Crash logs use the EMERGENCY log level
* [FEATURE] Global: Warn developers on SDK errors in the Logcat (cf `Datadog.setVerbosity()`)
* [FEATURE] Global: Expose the `Datadog.isInitialized()` method to the public API
* [OTHER] Deprecate the `Datadog.switchEndpoint()` method
* [OTHER] Fail silently when the SDK is not initialized

# 1.2.2 / 2020-02-26

### Changes

* [BUGFIX] Fix invalid dependency group in `dd-sdk-android-timber`. See [#147](https://github.com/DataDog/dd-sdk-android/issues/147) (Thanks [@mduong](https://github.com/mduong), [@alparp27](https://github.com/alparp27), [@rafaela-stockx](https://github.com/rafaela-stockx))

# 1.2.1 / 2020-02-19

### Changes

* [BUGFIX] Fix invalid dependency version in `dd-sdk-android-timber`. See [#138](https://github.com/DataDog/dd-sdk-android/issues/138) (Thanks [@mduong](https://github.com/mduong))

# 1.2.0 / 2020-01-20

### Changes

* [BUGFIX] Fail silently when trying to initialize the SDK twice. See #86 (Thanks [@Vavassor](https://github.com/Vavassor))
* [BUGFIX] Publish the Timber artifact automatically. See #90 (Thanks [@Macarse](https://github.com/Macarse))
* [FEATURE] Create a Crash Handler : App crashes will be automatically logged.
* [FEATURE] Downgrade OkHttp4 to OkHttp3
* [FEATURE] Make Library compatible with API 19+
* [FEATURE] Trigger background upload when the app is used offline
* [FEATURE] Use DownloadSpeed and signal strength to add info on connectivity
* [FEATURE] Use Gzip for log upload requests
* [OTHER] Analyse Benchmark reports in the CI
* [OTHER] Fix the flaky test in DataDogTimeProviderTest
* [OTHER] Generate a report on the SDK API changes ([dd-sdk-android/apiSurface](dd-sdk-android/apiSurface))

# 1.1.1 / 2020-01-07

### Changes

* [BUGFIX] Fix crash on Android Lollipop and Nougat

# 1.1.0 / 2020-01-06

### Changes

* [BUGFIX] Make the packageVersion field optional in the SDK initialisation
* [BUGFIX] Fix timestamp formatting in logs
* [FEATURE] Add a developer targeted logger 
* [FEATURE] Add user info in logs
* [FEATURE] Create automatic Tags / Attribute (app / sdk version)
* [FEATURE] Integrate SDK with Timber
* [IMPROVEMENT] Remove the obfuscation in the logs (faster local processing)
* [IMPROVEMENT] Implement a modern NetworkInfoProvider

# 1.0.0 / 2019-12-17

### Changes

* [BUGFIX] Make sure no logs are lost
* [FEATURE] Allow adding a throwable to logged messages
* [FEATURE] Automatically add the Logger name and thread to the logged messages
* [FEATURE] Allow Changing the endpoint between US and EU servers
* [FEATURE] Allow logger to add attributes for a single log
* [IMPROVEMENT] Remove the usage of the Android WorkManager
* [IMPROVEMENT] Improved overall performances
* [IMPROVEMENT] Disable background job when network or battery are low
* [OTHER] Secure the project with lints, tests and benchmarks
