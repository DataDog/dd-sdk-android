# 2.16.0 / 2024-11-20

* [FEATURE] Session Replay: Create Session Replay Compose module.
  See [#1879](https://github.com/DataDog/dd-sdk-android/pull/1879)
* [FEATURE] Session Replay: Add `Tab` and `TabRow` Composable groups mappers.
  See [#2171](https://github.com/DataDog/dd-sdk-android/pull/2171)
* [FEATURE] Session Replay: Add Abstract and Text semantics mapper for Compose Session Replay.
  See [#2292](https://github.com/DataDog/dd-sdk-android/pull/2292)
* [FEATURE] Session Replay: Add Semantics Mapper for Button role.
  See [#2296](https://github.com/DataDog/dd-sdk-android/pull/2296)
* [FEATURE] Session Replay: Add `ImageSemanticsNodeMapper` to support image role for Session Replay.
  See [#2322](https://github.com/DataDog/dd-sdk-android/pull/2322)
* [FEATURE] Session Replay: Add Tab semantics mapper.
  See [#2378](https://github.com/DataDog/dd-sdk-android/pull/2378)
* [FEATURE] Session Replay: Add `RadioButton` Semantics Node Mapper.
  See [#2381](https://github.com/DataDog/dd-sdk-android/pull/2381)
* [FEATURE] Session Replay: Add Material Chip mapper and improve `CompoundButton` telemetry.
  See [#2364](https://github.com/DataDog/dd-sdk-android/pull/2364)
* [FEATURE] Session Replay: Add Compose Session Replay scenario for benchmark sample application.
  See [#2379](https://github.com/DataDog/dd-sdk-android/pull/2379)
* [FEATURE] Session Replay: Add multiple extension support.
  See [#2384](https://github.com/DataDog/dd-sdk-android/pull/2384)
* [FEATURE] Session Replay: Add Compose Session Replay selector sample screen.
  See [#2394](https://github.com/DataDog/dd-sdk-android/pull/2394)
* [FEATURE] Session Replay: Add `AndroidComposeViewMapper` to support popup.
  See [#2395](https://github.com/DataDog/dd-sdk-android/pull/2395)
* [FEATURE] Session Replay: Integrate benchmark profiler in Compose mapper.
  See [#2397](https://github.com/DataDog/dd-sdk-android/pull/2397)
* [IMPROVEMENT] Add `MethodCall` telemetry for compose mapper.
  See [#2123](https://github.com/DataDog/dd-sdk-android/pull/2123)
* [IMPROVEMENT] Apply privacy settings to `TextCompositionGroupMapper` for Compose.
  See [#2121](https://github.com/DataDog/dd-sdk-android/pull/2121)
* [IMPROVEMENT] Use `SurfaceCompositionGroupMapper` to support container components in Session
  Replay.
  See [#2182](https://github.com/DataDog/dd-sdk-android/pull/2182)
* [IMPROVEMENT] Fix padding and resizing issue for `ImageView` mapper.
  See [#2372](https://github.com/DataDog/dd-sdk-android/pull/2372)
* [IMPROVEMENT] Add warning log when initializing the SDK outside of the main process.
  See [#2376](https://github.com/DataDog/dd-sdk-android/pull/2376)
* [IMPROVEMENT] breaking API change: Allow typed `Sampler`.
  See [#2385](https://github.com/DataDog/dd-sdk-android/pull/2385)
* [IMPROVEMENT] Create the `DeterministicSampler`.
  See [#2387](https://github.com/DataDog/dd-sdk-android/pull/2387)
* [IMPROVEMENT] Use deterministic sampling by default when tracing.
  See [#2388](https://github.com/DataDog/dd-sdk-android/pull/2388)
* [IMPROVEMENT] Align log levels for Session Replay already enabled.
  See [#2399](https://github.com/DataDog/dd-sdk-android/pull/2399)
* [IMPROVEMENT] Adjust Webview Replay storage configuration limits.
  See [#2400](https://github.com/DataDog/dd-sdk-android/pull/2400)
* [MAINTENANCE] Update Gradle to version `8.10.2`.
  See [#2359](https://github.com/DataDog/dd-sdk-android/pull/2359)
* [MAINTENANCE] Fix `ButtonCompositionGroupMapper` crash while calculating the corner radius.
  See [#2173](https://github.com/DataDog/dd-sdk-android/pull/2173)
* [MAINTENANCE] Fix Image reflection issue and update ProGuard rules.
  See [#2337](https://github.com/DataDog/dd-sdk-android/pull/2337)
* [MAINTENANCE] Fix `CompoundButton` mapper drawable clone issue.
  See [#2365](https://github.com/DataDog/dd-sdk-android/pull/2365)
* [MAINTENANCE] Fix crash while using recycled bitmap in Session Replay.
  See [#2396](https://github.com/DataDog/dd-sdk-android/pull/2396)
* [MAINTENANCE] Add experimental annotation for Session Replay for Compose.
  See [#2377](https://github.com/DataDog/dd-sdk-android/pull/2377)
* [MAINTENANCE] Lazy RUM raw event creation in event generator methods.
  See [#2363](https://github.com/DataDog/dd-sdk-android/pull/2363)
* [MAINTENANCE] Remove legacy code using Compose `sourceInfo`.
  See [#2386](https://github.com/DataDog/dd-sdk-android/pull/2386)

# 2.15.1 / 2024-11-04

* [MAINTENANCE] Fix `resolveResourceId` not correctly calling job finished when drawable cloning
  failed [#2367](https://github.com/DataDog/dd-sdk-android/pull/2367)

# 2.15.0 / 2024-10-28

* [FEATURE] Add `TimeBank` in Session Replay recorder for dynamic optimisation See [#2247](https://github.com/DataDog/dd-sdk-android/pull/2247)
* [FEATURE] Add Session Replay skipped frames count in `session ended` metrics. See [#2256](https://github.com/DataDog/dd-sdk-android/pull/2256)
* [FEATURE] Add a touch privacy override. See [#2334](https://github.com/DataDog/dd-sdk-android/pull/2334)
* [FEATURE] Add precheck conditions when registering the Session Replay feature. See [#2264](https://github.com/DataDog/dd-sdk-android/pull/2264)
* [FEATURE] Add a privacy override for hidden views. See [#2291](https://github.com/DataDog/dd-sdk-android/pull/2291)
* [FEATURE] Add image and textAndInput privacy overrides. See [#2312](https://github.com/DataDog/dd-sdk-android/pull/2312)
* [IMPROVEMENT] Add a dynamic optimization configuration field in `SessionReplayConfiguration`. See [#2259](https://github.com/DataDog/dd-sdk-android/pull/2259)
* [IMPROVEMENT] Use layout text to display `TextView` overflow correctly. See [#2279](https://github.com/DataDog/dd-sdk-android/pull/2279)
* [IMPROVEMENT] Remove the Session Replay `ButtonMapper` border. See [#2280](https://github.com/DataDog/dd-sdk-android/pull/2280)
* [IMPROVEMENT] Force single core for Session Replay. See [#2324](https://github.com/DataDog/dd-sdk-android/pull/2324)
* [IMPROVEMENT] Add a `ViewGroups` Session Replay demo screen in sample app. See [#2285](https://github.com/DataDog/dd-sdk-android/pull/2285)
* [IMPROVEMENT] Run integration tests on API 35 in the testing pyramid. See [#2272](https://github.com/DataDog/dd-sdk-android/pull/2272)
* [IMPROVEMENT] Add `MaterialCardView` support in the Material Session Replay extension. See [#2290](https://github.com/DataDog/dd-sdk-android/pull/2290)
* [IMPROVEMENT] Use an SDK source value in the Session Replay `MobileSegment.source` property. See [#2293](https://github.com/DataDog/dd-sdk-android/pull/2293)
* [IMPROVEMENT] Update the Session Replay schema with a Kotlin Multiplatform source for Mobile segment. See [#2297](https://github.com/DataDog/dd-sdk-android/pull/2297)
* [IMPROVEMENT] Improve test coverage of core unit tests. See [#2294](https://github.com/DataDog/dd-sdk-android/pull/2294)
* [IMPROVEMENT] Improve unit test coverage for RUM, Logs and Trace features. See [#2299](https://github.com/DataDog/dd-sdk-android/pull/2299)
* [IMPROVEMENT] Send retry information into RUM data upload requests. See [#2298](https://github.com/DataDog/dd-sdk-android/pull/2298)
* [IMPROVEMENT] Make the `DataOkHttpUploader` state volatile. See [#2305](https://github.com/DataDog/dd-sdk-android/pull/2305)
* [IMPROVEMENT] Read Session Replay system requirements synchronously with strict mode allowance. See [#2307](https://github.com/DataDog/dd-sdk-android/pull/2307)
* [IMPROVEMENT] Override process importance for Session Replay integration tests. See [#2304](https://github.com/DataDog/dd-sdk-android/pull/2304)
* [IMPROVEMENT] Detekt the api coverage in integration tests. See [#2300](https://github.com/DataDog/dd-sdk-android/pull/2300)
* [IMPROVEMENT] Resolve `PorterDuffColorFilter` case in drawable to color mapper. See [#2319](https://github.com/DataDog/dd-sdk-android/pull/2319)
* [IMPROVEMENT] Prevent obfuscation of Fine Grained Masking enums. See [#2321](https://github.com/DataDog/dd-sdk-android/pull/2321)
* [IMPROVEMENT] Make sure `ConsentAwareFileOrchestrator` is thread safe. See [#2313](https://github.com/DataDog/dd-sdk-android/pull/2313)
* [IMPROVEMENT] Improve RUM integration tests. See [#2317](https://github.com/DataDog/dd-sdk-android/pull/2317)
* [IMPROVEMENT] Add a default sample rate for Session Replay. See [#2323](https://github.com/DataDog/dd-sdk-android/pull/2323)
* [IMPROVEMENT] Remove batch metrics inner sampler to increase sample rate. See [#2328](https://github.com/DataDog/dd-sdk-android/pull/2328)
* [IMPROVEMENT] Add missing integration test for Logs. See [#2330](https://github.com/DataDog/dd-sdk-android/pull/2330)
* [IMPROVEMENT] Update Session Replay integration test payloads. See [#2318](https://github.com/DataDog/dd-sdk-android/pull/2318)
* [MAINTENANCE] Update Datadog Agent to 1.41.0. See [#2331](https://github.com/DataDog/dd-sdk-android/pull/2331)
* [MAINTENANCE] Fix the decompression in Session Replay instrumented tests for API 21. See [#2341](https://github.com/DataDog/dd-sdk-android/pull/2341)
* [MAINTENANCE] Reactivate Session Replay instrumented test for API 21. See [#2342](https://github.com/DataDog/dd-sdk-android/pull/2342)
* [MAINTENANCE] Fix some flaky tests. See [#2281](https://github.com/DataDog/dd-sdk-android/pull/2281)
* [MAINTENANCE] Fix a StrictMode warning regarding I/O disk operation on the main thread. See [#2284](https://github.com/DataDog/dd-sdk-android/pull/2284)
* [MAINTENANCE] Fix flaky feature context integration tests. See [#2295](https://github.com/DataDog/dd-sdk-android/pull/2295)
* [MAINTENANCE] Fix `SeekBarWireframeMapper` flaky test. See [#2308](https://github.com/DataDog/dd-sdk-android/pull/2308)
* [MAINTENANCE] Fix `SpanEventSerializerTest` flakiness. See [#2311](https://github.com/DataDog/dd-sdk-android/pull/2311)
* [MAINTENANCE] Remove an unnecessary legacy privacy line from the sampleApplication. See [#2314](https://github.com/DataDog/dd-sdk-android/pull/2314)
* [MAINTENANCE] Use Java 11 bytecode for public modules. See [#2315](https://github.com/DataDog/dd-sdk-android/pull/2315)
* [MAINTENANCE] Fix RUM integration test `verifyViewEventsOnSwipe`. See [#2326](https://github.com/DataDog/dd-sdk-android/pull/2326)
* [MAINTENANCE] Fix the regression for the `TelemetryErrorEvent` with throwable. See [#2325](https://github.com/DataDog/dd-sdk-android/pull/2325)
* [MAINTENANCE] Fix the execution of legacy instrumentation tests in CI. See [#2329](https://github.com/DataDog/dd-sdk-android/pull/2329)

# 2.14.0 / 2024-09-25

* [FEATURE] Add stop and start APIs for Session Replay. See [#2169](https://github.com/DataDog/dd-sdk-android/pull/2169)
* [FEATURE] Add touch privacy fine grained masking API to Session Replay. See [#2196](https://github.com/DataDog/dd-sdk-android/pull/2196)
* [FEATURE] Add text and input privacy fine grained masking API to Session Replay. See [#2235](https://github.com/DataDog/dd-sdk-android/pull/2235)
* [FEATURE] Introduce the `RumMonitor#addViewLoadingTime` API. See [#2243](https://github.com/DataDog/dd-sdk-android/pull/2243)
* [FEATURE] Introduce the API usage telemetry event and API. See [#2258](https://github.com/DataDog/dd-sdk-android/pull/2258)
* [IMPROVEMENT] Enable Kotlin test fixtures support. See [#2234](https://github.com/DataDog/dd-sdk-android/pull/2234)
* [IMPROVEMENT] Add `isContainer` attribute to session replay span. See [#2244](https://github.com/DataDog/dd-sdk-android/pull/2244)
* [IMPROVEMENT] Update custom detekt CI Job. See [#2118](https://github.com/DataDog/dd-sdk-android/pull/2118)
* [IMPROVEMENT] Randomize privacy levels to support Fine Grained Masking in E2E. See [#2265](https://github.com/DataDog/dd-sdk-android/pull/2265)
* [IMPROVEMENT] Update AGP to 8.6.1. See [#2269](https://github.com/DataDog/dd-sdk-android/pull/2269)
* [IMPROVEMENT] Add telemetry and logs related with `RumMonitor#addViewLoadingTime` API. See [#2267](https://github.com/DataDog/dd-sdk-android/pull/2267)
* [IMPROVEMENT] Handle SSE requests. See [#2270](https://github.com/DataDog/dd-sdk-android/pull/2270)
* [IMPROVEMENT] Do not use magic numbers in `InternalLogger` API. See [#2271](https://github.com/DataDog/dd-sdk-android/pull/2271)
* [IMPROVEMENT] Optimize MD5 byte array to hex string conversion. See [#2273](https://github.com/DataDog/dd-sdk-android/pull/2273)
* [IMPROVEMENT] `CONTRIBUTING` doc changes. See [#2275](https://github.com/DataDog/dd-sdk-android/pull/2275)
* [IMPROVEMENT] Add env tag in benchmark metrics. See [#2276](https://github.com/DataDog/dd-sdk-android/pull/2276)
* [MAINTENANCE] Make image privacy fine grained masking API public in Session Replay. See [#2204](https://github.com/DataDog/dd-sdk-android/pull/2204)
* [MAINTENANCE] Update benchmark metrics memory reader probe interval. See [#2228](https://github.com/DataDog/dd-sdk-android/pull/2228)
* [MAINTENANCE] Fix the flakiness in the `KioskTrackingTest`. See [#2226](https://github.com/DataDog/dd-sdk-android/pull/2226)
* [MAINTENANCE] Fix placeholder dimensions. See [#2248](https://github.com/DataDog/dd-sdk-android/pull/2248)
* [MAINTENANCE] Send fine grained masking instead of legacy privacy in config telemetry. See [#2253](https://github.com/DataDog/dd-sdk-android/pull/2253)
* [MAINTENANCE] Ensure `UploadWorker` uses the SDK instance name. See [#2257](https://github.com/DataDog/dd-sdk-android/pull/2257)
* [MAINTENANCE] Explicitly set `antlr-runtime` transitive dependency version. See [#2261](https://github.com/DataDog/dd-sdk-android/pull/2261)
* [MAINTENANCE] Add the integration tests related with `RumMonitor#addViewLoadingTime` API. See [#2268](https://github.com/DataDog/dd-sdk-android/pull/2268)
* [MAINTENANCE] Fix `DatadogInterceptor` flaky test. See [#2274](https://github.com/DataDog/dd-sdk-android/pull/2274)
* [MAINTENANCE] Fix typos and links in Github issue templates. See [#2277](https://github.com/DataDog/dd-sdk-android/pull/2277)

# 2.13.1 / 2024-09-09

* [BUGFIX] Stop upload worker on upload failure. See [#2242](https://github.com/DataDog/dd-sdk-android/pull/2242)

# 2.13.0 / 2024-09-03

* [FEATURE] Create Benchmark module to collect performance metrics. See [#2141](https://github.com/DataDog/dd-sdk-android/pull/2141)
* [BUGFIX] Use NO_EXPORT_FLAG for BroadcastReceiver on API above 26. See [#2170](https://github.com/DataDog/dd-sdk-android/pull/2170)
* [BUGFIX] Fix integration tests pipeline for API 21. See [#2197](https://github.com/DataDog/dd-sdk-android/pull/2197)
* [IMPROVEMENT] Added setSyntheticsAttribute in RumInternalProxy. See [#2133](https://github.com/DataDog/dd-sdk-android/pull/2133)
* [IMPROVEMENT] Use macos runner. See [#2154](https://github.com/DataDog/dd-sdk-android/pull/2154)
* [IMPROVEMENT] Remove obsolete nightly test references. See [#2157](https://github.com/DataDog/dd-sdk-android/pull/2157)
* [IMPROVEMENT] Add the integration tests for the SdkCore APIs. See [#2145](https://github.com/DataDog/dd-sdk-android/pull/2145)
* [IMPROVEMENT] Update link to troubleshooting documentation. See [#2164](https://github.com/DataDog/dd-sdk-android/pull/2164) (Thanks [@mateo-villa](https://github.com/mateo-villa))
* [IMPROVEMENT] Reset developerMode status when Datadog stop. See [#2174](https://github.com/DataDog/dd-sdk-android/pull/2174)
* [IMPROVEMENT] Extract logic to pull publishing credentials into a dedicated snippet. See [#2176](https://github.com/DataDog/dd-sdk-android/pull/2176)
* [IMPROVEMENT] Remove redundant build configuration in new reliability modules. See [#2178](https://github.com/DataDog/dd-sdk-android/pull/2178)
* [IMPROVEMENT] Remove image property from macOS-based jobs. See [#2181](https://github.com/DataDog/dd-sdk-android/pull/2181)
* [IMPROVEMENT] Update OkHttp to 4.12.0. See [#1975](https://github.com/DataDog/dd-sdk-android/pull/1975)
* [IMPROVEMENT] Speed up `IdGenerationStrategy` test. See [#2187](https://github.com/DataDog/dd-sdk-android/pull/2187)
* [IMPROVEMENT] Add integration tests for internal sdk core. See [#2177](https://github.com/DataDog/dd-sdk-android/pull/2177)
* [IMPROVEMENT] Update Gradle to 8.9 and AGP to 8.5.2. See [#2192](https://github.com/DataDog/dd-sdk-android/pull/2192)
* [IMPROVEMENT] Speed up generated files/licenses checks. See [#2188](https://github.com/DataDog/dd-sdk-android/pull/2188)
* [IMPROVEMENT] Log Timber tag. See [#2202](https://github.com/DataDog/dd-sdk-android/pull/2202)
* [IMPROVEMENT] Make sure user properties are immutable when setUserInfo. See [#2203](https://github.com/DataDog/dd-sdk-android/pull/2203)
* [IMPROVEMENT] Add the integration tests for FeatureScope public API. See [#2209](https://github.com/DataDog/dd-sdk-android/pull/2209)
* [IMPROVEMENT] Include optional exception in Upload Status. See [#2221](https://github.com/DataDog/dd-sdk-android/pull/2221)
* [IMPROVEMENT] Create UploadSchedulerStrategy interface and default implementation. See [#2222](https://github.com/DataDog/dd-sdk-android/pull/2222)
* [IMPROVEMENT] Add configuration to set uploadSchedulerStrategy. See [#2224](https://github.com/DataDog/dd-sdk-android/pull/2224)
* [IMPROVEMENT] Update `kotlinx.ast` dependency. See [#2231](https://github.com/DataDog/dd-sdk-android/pull/2231)

# 2.12.1 / 2024-08-13

* [BUGFIX] RUM: Make no-op RUM monitor implementation returned by default to be `NoOpAdvancedRumMonitor`. See [#2185](https://github.com/DataDog/dd-sdk-android/pull/2185)

# 2.12.0 / 2024-07-30

* [FEATURE] Trace: Add the `SessionEndedMetric` into sdk core. See [#2090](https://github.com/DataDog/dd-sdk-android/pull/2090)
* [FEATURE] SessionReplay: Use the datastore for Session Replay resources. See [#2041](https://github.com/DataDog/dd-sdk-android/pull/2041)
* [FEATURE] Trace: Provide 128 bits support for the trace ids in the Tracing sdk. See [#2089](https://github.com/DataDog/dd-sdk-android/pull/2089)
* [FEATURE] SessionReplay: Add api to clear all datastore data. See [#2096](https://github.com/DataDog/dd-sdk-android/pull/2096)
* [FEATURE] SessionReplay: Add `CompoundButton` mapper. See [#2120](https://github.com/DataDog/dd-sdk-android/pull/2120)
* [FEATURE] SessionReplay: Add API to configure the Image Privacy. See [#2125](https://github.com/DataDog/dd-sdk-android/pull/2125)
* [FEATURE] Trace: Introduce the `TraceContextInjection` to handle sampling in distributed traces. See [#2111](https://github.com/DataDog/dd-sdk-android/pull/2111)
* [IMPROVEMENT] Trace: Improve unit tests in Session metrics. See [#2095](https://github.com/DataDog/dd-sdk-android/pull/2095)
* [IMPROVEMENT] SessionReplay: Fix flaky test in `SeekBarWireframeMapperTest`. See [#2099](https://github.com/DataDog/dd-sdk-android/pull/2099)
* [IMPROVEMENT] Trace: Fix the Okhttp Otel parent span feature when not using RUM. See [#2100](https://github.com/DataDog/dd-sdk-android/pull/2100)
* [IMPROVEMENT] SessionReplay: Fix units for dropped nodes. See [#2107](https://github.com/DataDog/dd-sdk-android/pull/2107)
* [IMPROVEMENT] SessionReplay: Add TLVFormat DataStore persistence. See [#2038](https://github.com/DataDog/dd-sdk-android/pull/2038)
* [IMPROVEMENT] InternalMetrics: Add sampling rate to internal metrics. See [#2108](https://github.com/DataDog/dd-sdk-android/pull/2108)
* [IMPROVEMENT] SessionReplay: Fix `RumSessionEnded` metric flaky test. See [#2114](https://github.com/DataDog/dd-sdk-android/pull/2114)
* [IMPROVEMENT] SessionReplay: Use `BackpressureExecutor` for SessionReplay event processing. See [#2116](https://github.com/DataDog/dd-sdk-android/pull/2116)
* [IMPROVEMENT] SessionReplay: Improve CheckableTextViewMapper. See [#2115](https://github.com/DataDog/dd-sdk-android/pull/2115)
* [IMPROVEMENT] SessionReplay: `SwitchCompat` mapper improvement. See [#2117](https://github.com/DataDog/dd-sdk-android/pull/2117)
* [IMPROVEMENT] RUM: Fix the racing condition in the `RotatingDnsResolver` logic. See [#2127](https://github.com/DataDog/dd-sdk-android/pull/2127)
* [IMPROVEMENT] RUM: Add request id in okhttp request. See [#2126](https://github.com/DataDog/dd-sdk-android/pull/2126)
* [IMPROVEMENT] Trace: Make sure network local spans have `kind:client` tag. See [#2136](https://github.com/DataDog/dd-sdk-android/pull/2136)
* [IMPROVEMENT] Core: Increase retry delay on DNS error. See [#2135](https://github.com/DataDog/dd-sdk-android/pull/2135)

# 2.11.0 / 2024-06-20

* [FEATURE] Trace: Bundle `dd-trace-core` code into the `dd-sdk-android-trace` module. See [#1907](https://github.com/DataDog/dd-sdk-android/pull/1907)
* [FEATURE] Trace: Provide the correct sampling priority for our Span events based on APM new rules. See [#1913](https://github.com/DataDog/dd-sdk-android/pull/1913)
* [FEATURE] Trace: Add the `CoreTracer` tests. See [#1924](https://github.com/DataDog/dd-sdk-android/pull/1924)
* [FEATURE] Trace: Provide core tracer logger implementation. See [#1953](https://github.com/DataDog/dd-sdk-android/pull/1953)
* [FEATURE] Trace: Provide the `bundleWithRum` capability for `OtelTracer`. See [#1960](https://github.com/DataDog/dd-sdk-android/pull/1960)
* [FEATURE] Trace: Provide the `DatadogContextStorage` for OpenTelemetry. See [#1970](https://github.com/DataDog/dd-sdk-android/pull/1970)
* [FEATURE] Trace: Provide Otel bundle with logs feature. See [#1979](https://github.com/DataDog/dd-sdk-android/pull/1979)
* [FEATURE] Trace: Setup the trace end tests environment for Otel API. See [#1983](https://github.com/DataDog/dd-sdk-android/pull/1983)
* [FEATURE] Trace: Add the `SpanLink` support for Otel API implementation. See [#1993](https://github.com/DataDog/dd-sdk-android/pull/1993)
* [FEATURE] Trace: Add the Otel API feature integration tests. See [#1995](https://github.com/DataDog/dd-sdk-android/pull/1995)
* [FEATURE] Trace: Report OpenTelemetry data in the configuration telemetry. See [#2006](https://github.com/DataDog/dd-sdk-android/pull/2006)
* [FEATURE] Trace: Extract OpenTelemetry support SDK into a dedicated module. See [#2021](https://github.com/DataDog/dd-sdk-android/pull/2021)
* [FEATURE] Trace: Setup the CI and Gradle tests for the new `dd-sdk-android-trace-otel` module. See [#2035](https://github.com/DataDog/dd-sdk-android/pull/2035)
* [FEATURE] Trace: Enable desugaring for sample and single-fit apps. See [#2036](https://github.com/DataDog/dd-sdk-android/pull/2036)
* [FEATURE] Session Replay: Add support for progress bars. See [#2047](https://github.com/DataDog/dd-sdk-android/pull/2047)
* [FEATURE] Trace: Add OpenTelemetry use case into the Wear sample app. See [#2068](https://github.com/DataDog/dd-sdk-android/pull/2068)
* [FEATURE] Trace: Add OpenTelemetry use case into the `vendor-lib` sample. See [#2069](https://github.com/DataDog/dd-sdk-android/pull/2069)
* [FEATURE] Trace: Add the OkHttp Otel extensions module. See [#2073](https://github.com/DataDog/dd-sdk-android/pull/2073)
* [FEATURE] Trace: `OtelTraceProvider.Builder`: introduce the trace rate limit property. See [#2086](https://github.com/DataDog/dd-sdk-android/pull/2086)
* [BUGFIX] Session Replay: Fix time drift in `RecordedDataQueueHandler`. See [#2075](https://github.com/DataDog/dd-sdk-android/pull/2075)
* [IMPROVEMENT] Trace: Remove some unused IAST/CI Visibility classes. See [#2000](https://github.com/DataDog/dd-sdk-android/pull/2000)
* [IMPROVEMENT] Trace: Remove `moshi` dependency from trace module. See [#2003](https://github.com/DataDog/dd-sdk-android/pull/2003)
* [IMPROVEMENT] Fix some detekt issues. See [#2043](https://github.com/DataDog/dd-sdk-android/pull/2043)
* [IMPROVEMENT] Session Replay: Delegate `Drawable` copy to background thread. See [#2048](https://github.com/DataDog/dd-sdk-android/pull/2048)
* [IMPROVEMENT] Trace: Make `CoreTracer` code Java 7 compatible. See [#2051](https://github.com/DataDog/dd-sdk-android/pull/2051)
* [IMPROVEMENT] Session Replay: Improve telemetry from `RecordedDataQueueHandler`. See [#2053](https://github.com/DataDog/dd-sdk-android/pull/2053)
* [IMPROVEMENT] Global: Fix thread safety warnings. See [#2056](https://github.com/DataDog/dd-sdk-android/pull/2056)
* [IMPROVEMENT] Trace: Remove the `dd-sketches` dependency and related logic. See [#2062](https://github.com/DataDog/dd-sdk-android/pull/2062)
* [IMPROVEMENT] Trace: Fix the `jctools` Proguard rules. See [#2063](https://github.com/DataDog/dd-sdk-android/pull/2063)
* [IMPROVEMENT] Add ProGuard rules to sample app. See [#2067](https://github.com/DataDog/dd-sdk-android/pull/2067)
* [IMPROVEMENT] Session Replay: Improve `ButtonMapper`. See [#2070](https://github.com/DataDog/dd-sdk-android/pull/2070)
* [IMPROVEMENT] Trace: Remove some unused code from tracing module. See [#2079](https://github.com/DataDog/dd-sdk-android/pull/2079)
* [IMPROVEMENT] Trace: Add OpenTelemetry Proguard rules for compile-only annotations. See [#2080](https://github.com/DataDog/dd-sdk-android/pull/2080)
* [IMPROVEMENT] Trace: Fix the `CoreTracer` flaky tests. See [#2081](https://github.com/DataDog/dd-sdk-android/pull/2081)
* [IMPROVEMENT] Trace: Remove System and Environment config source in the `CoreTracer`. See [#2084](https://github.com/DataDog/dd-sdk-android/pull/2084)
* [IMPROVEMENT] Remove duplicated Proguard configuration in the sample app. See [#2088](https://github.com/DataDog/dd-sdk-android/pull/2088)
* [IMPROVEMENT] Session Replay: Granular telemetry sampling for mappers. See [#2087](https://github.com/DataDog/dd-sdk-android/pull/2087)
* [MAINTENANCE] Merge develop branch. See [#1948](https://github.com/DataDog/dd-sdk-android/pull/1948)
* [MAINTENANCE] Merge `develop` branch into `feature/otel-support` branch. See [#1998](https://github.com/DataDog/dd-sdk-android/pull/1998)
* [MAINTENANCE] Next dev iteration 2.11.0. See [#2050](https://github.com/DataDog/dd-sdk-android/pull/2050)
* [MAINTENANCE] Merge `release/2.10.0` branch into `develop` branch. See [#2054](https://github.com/DataDog/dd-sdk-android/pull/2054)
* [MAINTENANCE] Merge `develop` branch into `feature/otel-support` branch. See [#2058](https://github.com/DataDog/dd-sdk-android/pull/2058)
* [MAINTENANCE] Merge release `2.10.1` into `develop` branch. See [#2065](https://github.com/DataDog/dd-sdk-android/pull/2065)
* [MAINTENANCE] Merge develop branch. See [#2076](https://github.com/DataDog/dd-sdk-android/pull/2076)
* [MAINTENANCE] Merge Otel feature branch. See [#2077](https://github.com/DataDog/dd-sdk-android/pull/2077)

# 2.10.1 / 2024-05-30

* [IMPROVEMENT] Reduce Method Call Sample Rate. See [#2060](https://github.com/DataDog/dd-sdk-android/pull/2060)
* [IMPROVEMENT] Limit total telemetry events sent per session. See [#2061](https://github.com/DataDog/dd-sdk-android/pull/2061)

# 2.10.0 / 2024-05-23

* [FEATURE] Global: Add Method Call Telemetry. See [#1940](https://github.com/DataDog/dd-sdk-android/pull/1940)
* [FEATURE] Session Replay: Add support to the `Toolbar` in Session Replay. See [#2024](https://github.com/DataDog/dd-sdk-android/pull/2024)
* [IMPROVEMENT] Session Replay: Improve masking arch. See [#2011](https://github.com/DataDog/dd-sdk-android/pull/2011)
* [IMPROVEMENT] Session Replay: Simplify generic type in mappers. See [#2015](https://github.com/DataDog/dd-sdk-android/pull/2015)
* [IMPROVEMENT] Global: Support additional properties in Telemetry Error events. See [#2025](https://github.com/DataDog/dd-sdk-android/pull/2025)
* [IMPROVEMENT] Session Replay: Add telemetry on SR resources track. See [#2027](https://github.com/DataDog/dd-sdk-android/pull/2027)
* [IMPROVEMENT] Session Replay: Add telemetry to detect uncovered View/Drawable in Session Replay. See [#2028](https://github.com/DataDog/dd-sdk-android/pull/2028)
* [IMPROVEMENT] Session Replay: Improve `SeekBarMapper`. See [#2037](https://github.com/DataDog/dd-sdk-android/pull/2037)
* [IMPROVEMENT] RUM: Flag critical events in custom persistence. See [#2044](https://github.com/DataDog/dd-sdk-android/pull/2044)
* [IMPROVEMENT] Delegate Drawable copy to background thread. See [#2048](https://github.com/DataDog/dd-sdk-android/pull/2048)
* [MAINTENANCE] Next dev iteration. See [#2020](https://github.com/DataDog/dd-sdk-android/pull/2020)
* [MAINTENANCE] Merge release `2.9.0` into `develop` branch. See [#2023](https://github.com/DataDog/dd-sdk-android/pull/2023)
* [MAINTENANCE] Session Replay: Improve UT for SR Obfuscators. See [#2031](https://github.com/DataDog/dd-sdk-android/pull/2031)
* [MAINTENANCE] Create package name consistency rule. See [#2032](https://github.com/DataDog/dd-sdk-android/pull/2032)
* [MAINTENANCE] Session Replay: Improve the `TextViewMapper` unit tests. See [#2034](https://github.com/DataDog/dd-sdk-android/pull/2034)
* [MAINTENANCE] Fix KtLint version in `local_ci` script. See [#2039](https://github.com/DataDog/dd-sdk-android/pull/2039)
* [MAINTENANCE] Session Replay: Fix SR flaky test. See [#2042](https://github.com/DataDog/dd-sdk-android/pull/2042)
* [MAINTENANCE] Global: Update the Method Call metric usage. See [#2040](https://github.com/DataDog/dd-sdk-android/pull/2040)
* [MAINTENANCE] Update static analysis pipeline version. See [#2045](https://github.com/DataDog/dd-sdk-android/pull/2045)
* [MAINTENANCE] Fix flaky test regarding `PerformanceMeasure` sampling rate. See [#2046](https://github.com/DataDog/dd-sdk-android/pull/2046)

# 2.9.0 / 2024-05-02

* [BUGFIX] RUM: Prevent crash in `JankStats` listener. See [#1981](https://github.com/DataDog/dd-sdk-android/pull/1981)
* [BUGFIX] RUM: Unregister vital listeners when view is stopped. See [#2009](https://github.com/DataDog/dd-sdk-android/pull/2009)
* [BUGFIX] Core: Fix `ConcurrentModificationException` during features iteration. See [#2012](https://github.com/DataDog/dd-sdk-android/pull/2012)
* [IMPROVEMENT] RUM: Optimise `BatchFileOrchestator` performance. See [#1968](https://github.com/DataDog/dd-sdk-android/pull/1968)
* [IMPROVEMENT] Use custom naming for threads created inside SDK. See [#1987](https://github.com/DataDog/dd-sdk-android/pull/1987)
* [IMPROVEMENT] Synchronize SR info with webviews. See [#1990](https://github.com/DataDog/dd-sdk-android/pull/1990)
* [IMPROVEMENT] Core: Start sending batches immediately after feature is initialized. See [#1991](https://github.com/DataDog/dd-sdk-android/pull/1991)
* [IMRPOVEMENT] Create RUM Feature Integration Tests. See [#2004](https://github.com/DataDog/dd-sdk-android/pull/2004)
* [IMRROVEMENT] Make constructors of `DatadogSite` private. See [#2010](https://github.com/DataDog/dd-sdk-android/pull/2010)
* [IMRROVEMENT] Log warning about tag modification only once. See [#2017](https://github.com/DataDog/dd-sdk-android/pull/2017)
* [IMRROVEMENT] Add status code in user-facing message in case of `UnknownError` during batch upload. See [#2018](https://github.com/DataDog/dd-sdk-android/pull/2018)
* [MAINTENANCE] Next dev iteration. See [#1972](https://github.com/DataDog/dd-sdk-android/pull/1972)
* [MAINTENANCE] Remove non-ASCII characters from test names. See [#1973](https://github.com/DataDog/dd-sdk-android/pull/1973)
* [MAINTENANCE] Update Kotlin to 1.8.22, Gradle to 8.2.1, update related tooling. See [#1974](https://github.com/DataDog/dd-sdk-android/pull/1974)
* [MAINTENANCE] Merge `release/2.8.0` branch into `develop` branch. See [#1977](https://github.com/DataDog/dd-sdk-android/pull/1977)
* [MAINTENANCE] Switch to the Golden Base Image for Docker. See [#1982](https://github.com/DataDog/dd-sdk-android/pull/1982)
* [MAINTENANCE] Remove unused Maven Model dependency. See [#1989](https://github.com/DataDog/dd-sdk-android/pull/1989)
* [MAINTENANCE] Update testing ci steps to limit OOM and memory usage. See [#1986](https://github.com/DataDog/dd-sdk-android/pull/1986)
* [MAINTENANCE] Upload sample app to rum playground. See [#1994](https://github.com/DataDog/dd-sdk-android/pull/1994)
* [MAINTENANCE] Update copyright. See [#1992](https://github.com/DataDog/dd-sdk-android/pull/1992)
* [MAINTENANCE] Don't mark internal extension functions for 3rd party types as 3rd party. See [#1996](https://github.com/DataDog/dd-sdk-android/pull/1996)
* [MAINTENANCE] Use credentials for the right org. See [#1997](https://github.com/DataDog/dd-sdk-android/pull/1997)
* [MAINTENANCE] Update Detekt API version used to 1.23.0. See [#1988](https://github.com/DataDog/dd-sdk-android/pull/1988)
* [MAINTENANCE] Remove the usage of deprecated `TestConfig` constructor. See [#1999](https://github.com/DataDog/dd-sdk-android/pull/1999)
* [MAINTENANCE] Fix flakyness in SR unit tests. See [#2001](https://github.com/DataDog/dd-sdk-android/pull/2001)
* [MAINTENANCE] Remove legacy nightly tests. See [#2005](https://github.com/DataDog/dd-sdk-android/pull/2005)
* [MAINTENANCE] Redirect slack notif to mobile-sdk-ops channel. See [#2007](https://github.com/DataDog/dd-sdk-android/pull/2007)

# 2.8.0 / 2024-04-09

* [FEATURE] Add `buildId` to the RUM error and Log events. See [#1756](https://github.com/DataDog/dd-sdk-android/pull/1756)
* [FEATURE] WebView Session Replay: Implement WebView bridge getCapabilities. See [#1871](https://github.com/DataDog/dd-sdk-android/pull/1871)
* [FEATURE] RUM: Call RUM error mapper even for crashes. See [#1945](https://github.com/DataDog/dd-sdk-android/pull/1945)
* [FEATURE] RUM: Report time since the application start for crashes in RUM. See [#1961](https://github.com/DataDog/dd-sdk-android/pull/1961)
* [BUGFIX] RUM: Fix application startup time regression. See [#1935](https://github.com/DataDog/dd-sdk-android/pull/1935)
* [BUGFIX] Session Replay: Prevent crashing the host app in the `ViewOnDrawInterceptor`. See [#1951](https://github.com/DataDog/dd-sdk-android/pull/1951)
* [BUGFIX] Session Replay: Prevent crash in Canvas Wrapper. See [#1954](https://github.com/DataDog/dd-sdk-android/pull/1954)
* [BUGFIX] RUM: Safe getting of Intent extras. See [#1950](https://github.com/DataDog/dd-sdk-android/pull/1950)
* [BUGFIX] RUM: Don't traverse non-visible ViewGroups for searching user interaction targets. See [#1969](https://github.com/DataDog/dd-sdk-android/pull/1969)
* [IMPROVEMENT] WebView Session Replay: Introduce the `FeatureContextUpdateListener` API. See [#1829](https://github.com/DataDog/dd-sdk-android/pull/1829)
* [IMPROVEMENT] WebView Session Replay: Provide the parent container information for browser rum events. See [#1831](https://github.com/DataDog/dd-sdk-android/pull/1831)
* [IMPROVEMENT] WebView Session Replay: Detect full snapshot from WebView session replay. See [#1908](https://github.com/DataDog/dd-sdk-android/pull/1908)
* [IMPROVEMENT] Session Replay: Refactor and split classes. See [#1873](https://github.com/DataDog/dd-sdk-android/pull/1873)
* [IMPROVEMENT] WebView Session Replay: Keep WebView wireframe hidden. See [#1949](https://github.com/DataDog/dd-sdk-android/pull/1949)
* [IMPROVEMENT] Remove Runtime shutdown hook when SDK instance is stopped. See [#1956](https://github.com/DataDog/dd-sdk-android/pull/1956)
* [IMPROVEMENT] Fix message when writer is NoOp. See [#1963](https://github.com/DataDog/dd-sdk-android/pull/1963)
* [IMPROVEMENT] Global: Make sure `error.threads` always have content from `error.stack`. See [#1964](https://github.com/DataDog/dd-sdk-android/pull/1964)
* [MAINTENANCE] Merge develop branch. See [#1849](https://github.com/DataDog/dd-sdk-android/pull/1849)
* [MAINTENANCE] Merge develop. See [#1915](https://github.com/DataDog/dd-sdk-android/pull/1915)
* [MAINTENANCE] Merge develop into Session Replay WebView feature branch. See [#1917](https://github.com/DataDog/dd-sdk-android/pull/1917)
* [MAINTENANCE] Merge develop into `feature/sr-webview`. See [#1922](https://github.com/DataDog/dd-sdk-android/pull/1922)
* [MAINTENANCE] Next dev iteration. See [#1928](https://github.com/DataDog/dd-sdk-android/pull/1928)
* [MAINTENANCE] Merge release 2.7.0 into `develop` branch. See [#1930](https://github.com/DataDog/dd-sdk-android/pull/1930)
* [MAINTENANCE] Address some flaky tests. See [#1934](https://github.com/DataDog/dd-sdk-android/pull/1934)
* [MAINTENANCE] Add a test for the safe events serialization produced by `RumViewScope` in multi-threaded environment. See [#1933](https://github.com/DataDog/dd-sdk-android/pull/1933)
* [MAINTENANCE] Fix mime type for nightly tests. See [#1936](https://github.com/DataDog/dd-sdk-android/pull/1936)
* [MAINTENANCE] Disable some Session Replay integration tests temporarily due to flakiness. See [#1941](https://github.com/DataDog/dd-sdk-android/pull/1941)
* [MAINTENANCE] Merge 2.7.1 on develop. See [#1947](https://github.com/DataDog/dd-sdk-android/pull/1947)
* [MAINTENANCE] Improve TODO detekt rule. See [#1955](https://github.com/DataDog/dd-sdk-android/pull/1955)
* [MAINTENANCE] Disable flaky Session Replay test. See [#1957](https://github.com/DataDog/dd-sdk-android/pull/1957)
* [MAINTENANCE] Merge develop. See [#1958](https://github.com/DataDog/dd-sdk-android/pull/1958)
* [MAINTENANCE] Merge `feature/sr-web-view-support`. See [#1959](https://github.com/DataDog/dd-sdk-android/pull/1959)
* [MAINTENANCE] Fix flaky `TodoWithoutTask` tests. See [#1962](https://github.com/DataDog/dd-sdk-android/pull/1962)
* [MAINTENANCE] Fix flaky `DatadogCore` test. See [#1965](https://github.com/DataDog/dd-sdk-android/pull/1965)
* [MAINTENANCE] Update actions for running CodeQL workflow. See [#1966](https://github.com/DataDog/dd-sdk-android/pull/1966)
* [MAINTENANCE] Fix flaky tests. See [#1967](https://github.com/DataDog/dd-sdk-android/pull/1967)

# 2.7.1 / 2024-03-27

* [BUGFIX] RUM: Improve adding Feature Flag evaluation(s) performance.
  See [#1932](https://github.com/DataDog/dd-sdk-android/pull/1932)
* [MAINTENANCE] Core: add a BackPressure strategy to limit the load on background threads and get notified when capacity is reached.
  See [#1938](https://github.com/DataDog/dd-sdk-android/pull/1938) and [#1939](https://github.com/DataDog/dd-sdk-android/pull/1939)

# 2.7.0 / 2024-03-21

* [FEATURE] Session Replay: Add a request builder for resources. See [#1827](https://github.com/DataDog/dd-sdk-android/pull/1827)
* [FEATURE] Session Replay: Add Resources feature. See [#1840](https://github.com/DataDog/dd-sdk-android/pull/1840)
* [FEATURE] Session Replay: Implement resource capture during traversal. See [#1854](https://github.com/DataDog/dd-sdk-android/pull/1854)
* [FEATURE] Add `source_type` when sent from cross platform logs. See [#1895](https://github.com/DataDog/dd-sdk-android/pull/1895)
* [FEATURE] Session Replay: Enable Resource Endpoint by default. See [#1858](https://github.com/DataDog/dd-sdk-android/pull/1858)
* [FEATURE] Logs: Add support for global attributes on logs. See [#1900](https://github.com/DataDog/dd-sdk-android/pull/1900)
* [FEATURE] RUM: Allow setting custom error fingerprint. See [#1911](https://github.com/DataDog/dd-sdk-android/pull/1911)
* [FEATURE] RUM: Report all threads for non-fatal ANRs. See [#1912](https://github.com/DataDog/dd-sdk-android/pull/1912)
* [FEATURE] RUM: Report fatal ANRs. See [#1909](https://github.com/DataDog/dd-sdk-android/pull/1909)
* [BUGFIX] Session Replay: Avoid crash when `applicationContext` is `null`. See [#1864](https://github.com/DataDog/dd-sdk-android/pull/1864)
* [BUGFIX] Session Replay: Fix image resizing issue. See [#1897](https://github.com/DataDog/dd-sdk-android/pull/1897)
* [BUGFIX] Fix typo in source type. See [#1904](https://github.com/DataDog/dd-sdk-android/pull/1904)
* [BUGFIX] RUM: Prevent `ConcurrentModificationException` when reading feature flags. See [#1925](https://github.com/DataDog/dd-sdk-android/pull/1925)
* [IMPROVEMENT] RUM: Disable non-fatal ANR reporting by default. See [#1914](https://github.com/DataDog/dd-sdk-android/pull/1914)
* [IMPROVEMENT] RUM: Introduce `error.category` attribute for exceptions, categorize ANRs separately. See [#1918](https://github.com/DataDog/dd-sdk-android/pull/1918)
* [MAINTENANCE] Next dev iteration. See [#1861](https://github.com/DataDog/dd-sdk-android/pull/1861)
* [MAINTENANCE] Merge `release/2.6.0` in `develop`. See [#1862](https://github.com/DataDog/dd-sdk-android/pull/1862)
* [MAINTENANCE] Merge `release/2.6.1` changes into `develop` branch. See [#1868](https://github.com/DataDog/dd-sdk-android/pull/1868)
* [MAINTENANCE] Update telemetry schema. See [#1874](https://github.com/DataDog/dd-sdk-android/pull/1874)
* [MAINTENANCE] Merge Hotfix 2.6.2. See [#1890](https://github.com/DataDog/dd-sdk-android/pull/1890)
* [MAINTENANCE] Add signed commits requirement to `CONTRIBUTING.md`. See [#1905](https://github.com/DataDog/dd-sdk-android/pull/1905)
* [MAINTENANCE] Session Replay: Cleanup SR code. See [#1910](https://github.com/DataDog/dd-sdk-android/pull/1910)
* [MAINTENANCE] Session Replay: Fix integration tests post Session Replay refactoring. See [#1916](https://github.com/DataDog/dd-sdk-android/pull/1916)
* [MAINTENANCE] Session Replay: Fix `SrImageButtonsMaskUserInputTest`. See [#1920](https://github.com/DataDog/dd-sdk-android/pull/1920)
* [MAINTENANCE] Adjust `ktlint` formatting rules. See [#1919](https://github.com/DataDog/dd-sdk-android/pull/1919)
* [MAINTENANCE] Fix formatting. See [#1921](https://github.com/DataDog/dd-sdk-android/pull/1921)

# 2.6.2 / 2024-02-23

* [BUGFIX] RUM: Fix crash in frame rate vital detection. See [#1872](https://github.com/DataDog/dd-sdk-android/pull/1872)

# 2.6.1 / 2024-02-21

* [BUGFIX] RUM: Fix missing source in telemetry json schema. See [#1865](https://github.com/DataDog/dd-sdk-android/pull/1865)
* [MAINTENANCE] RUM: Remove stale json schema file. See [#1866](https://github.com/DataDog/dd-sdk-android/pull/1866)

# 2.6.0 / 2024-02-19

* [FEATURE] RUM\Logs: Report all threads in case of crash. See [#1848](https://github.com/DataDog/dd-sdk-android/pull/1848)
* [BUGFIX] RUM: Make a copy of attributes before passing them to RUM event. See [#1830](https://github.com/DataDog/dd-sdk-android/pull/1830)
* [BUGFIX] Session Replay: Add traversal flag to snapshot items. See [#1837](https://github.com/DataDog/dd-sdk-android/pull/1837)
* [BUGFIX] Drop batch telemetry where duration or age have negative values. See [#1850](https://github.com/DataDog/dd-sdk-android/pull/1850)
* [BUGFIX] RUM: Do not update RUM View global properties after the view is stopped. See [#1851](https://github.com/DataDog/dd-sdk-android/pull/1851)
* [IMPROVEMENT] RUM: Improve vital support for higher refresh rate devices. See [#1806](https://github.com/DataDog/dd-sdk-android/pull/1806)
* [IMPROVEMENT] RUM: Add more HTTP methods to RUM. See [#1826](https://github.com/DataDog/dd-sdk-android/pull/1826)
* [IMPROVEMENT] RUM: Start session when RUM is initialized. See [#1832](https://github.com/DataDog/dd-sdk-android/pull/1832)
* [IMPROVEMENT] RUM: Add new error source types to RUM schema. See [#1855](https://github.com/DataDog/dd-sdk-android/pull/1855)
* [IMPROVEMENT] RUM: Set `source_type` on native crashes to `ndk`. See [#1856](https://github.com/DataDog/dd-sdk-android/pull/1856)
* [MAINTENANCE] Next dev iteration 2.6.0. See [#1823](https://github.com/DataDog/dd-sdk-android/pull/1823)
* [MAINTENANCE] Merge `release/2.5.0` branch into `develop` branch. See [#1825](https://github.com/DataDog/dd-sdk-android/pull/1825)
* [MAINTENANCE] Update RUM Schema. See [#1828](https://github.com/DataDog/dd-sdk-android/pull/1828)
* [MAINTENANCE] Merge 2.5.1 into develop. See [#1842](https://github.com/DataDog/dd-sdk-android/pull/1842)
* [MAINTENANCE] Introduce github issue forms. See [#1852](https://github.com/DataDog/dd-sdk-android/pull/1852)

# 2.5.1 / 2024-01-24

* [BUGFIX] RUM: Prevent crash due to concurrent modification of custom attributes. See [#1838](https://github.com/DataDog/dd-sdk-android/pull/1838)

# 2.5.0 / 2024-01-15

* [FEATURE] Add accessor for current session id. See [#1810](https://github.com/DataDog/dd-sdk-android/pull/1810)
* [BUGFIX] Session Replay: Enable recording session if first RUM message happened before init. See [#1777](https://github.com/DataDog/dd-sdk-android/pull/1777)
* [BUGFIX] RUM: Fix view url in case of `NavigationViewTrackingStrategy` usage. See [#1791](https://github.com/DataDog/dd-sdk-android/pull/1791)
* [BUGFIX] Session Replay: Fix `ConcurrentModificationException` in `BitmapPool`. See [#1798](https://github.com/DataDog/dd-sdk-android/pull/1798)
* [BUGFIX] RUM: Use internal key for View Scopes. See [#1812](https://github.com/DataDog/dd-sdk-android/pull/1812)
* [BUGFIX] `getCurrentSessionId` returns correct value. See [#1817](https://github.com/DataDog/dd-sdk-android/pull/1817)
* [IMPROVEMENT] RUM: Better handling of event write errors in RUM. See [#1766](https://github.com/DataDog/dd-sdk-android/pull/1766)
* [IMPROVEMENT] Single Feature Integration Tests: Trace. See [#1786](https://github.com/DataDog/dd-sdk-android/pull/1786)
* [IMPROVEMENT] Optimize response body length reporting in OkHttp instrumentation. See [#1790](https://github.com/DataDog/dd-sdk-android/pull/1790)
* [IMPROVEMENT] RUM/Synthetics: Make synthetics logs more verbose. See [#1813](https://github.com/DataDog/dd-sdk-android/pull/1813)
* [IMPROVEMENT] Prevent false positive warning. See [#1815](https://github.com/DataDog/dd-sdk-android/pull/1815)
* [IMPROVEMENT] RUM: Safe serialization of user-provided attributes. See [#1818](https://github.com/DataDog/dd-sdk-android/pull/1818)
* [IMPROVEMENT] RUM: Add additional status codes as retryable. See [#1819](https://github.com/DataDog/dd-sdk-android/pull/1819)
* [MAINTENANCE] Merge `release/2.4.0` into `develop` branch. See [#1784](https://github.com/DataDog/dd-sdk-android/pull/1784)
* [MAINTENANCE] Add delay before executors are flushed and stopped in nightly tests. See [#1783](https://github.com/DataDog/dd-sdk-android/pull/1783)
* [MAINTENANCE] Upgrade shared CI pipeline. See [#1789](https://github.com/DataDog/dd-sdk-android/pull/1789)
* [MAINTENANCE] Add telemetry point for null file content. See [#1792](https://github.com/DataDog/dd-sdk-android/pull/1792)
* [MAINTENANCE] RUM: Migrate Realm from KAPT to KSP. See [#1794](https://github.com/DataDog/dd-sdk-android/pull/1794)
* [MAINTENANCE] Fix OOM in CI jobs. See [#1796](https://github.com/DataDog/dd-sdk-android/pull/1796)
* [MAINTENANCE] Increase wait time in NightlyTestRule. See [#1799](https://github.com/DataDog/dd-sdk-android/pull/1799)
* [MAINTENANCE] Use Datadog Agent 1.26.1. See [#1800](https://github.com/DataDog/dd-sdk-android/pull/1800)
* [MAINTENANCE] Move reading `BUILDENV_HOST_IP` variable to the job script definition. See [#1801](https://github.com/DataDog/dd-sdk-android/pull/1801)
* [MAINTENANCE] Use automatic Gradle daemon instrumentation with CI Visibility instead of manual test tasks instrumentation. See [#1804](https://github.com/DataDog/dd-sdk-android/pull/1804)

# 2.4.0 / 2023-12-21

* [FEATURE] Global: Create `PersistenceStrategy` interface. See [#1745](https://github.com/DataDog/dd-sdk-android/pull/1745)
* [FEATURE] Global: Let customer set custom persistence strategy in configuration. See [#1746](https://github.com/DataDog/dd-sdk-android/pull/1746)
* [FEATURE] Global: Implement `AbstractStorage`. See [#1747](https://github.com/DataDog/dd-sdk-android/pull/1747)
* [FEATURE] Global: Use `AbstractStorage` when custom persistence strategy provided. See [#1748](https://github.com/DataDog/dd-sdk-android/pull/1748)
* [FEATURE] RUM: Print RUM app, session and view ID in LogCat. See [#1760](https://github.com/DataDog/dd-sdk-android/pull/1760)
* [BUGFIX] Session Replay: Fix duplicate wireframes issue. See [#1761](https://github.com/DataDog/dd-sdk-android/pull/1761)
* [BUGFIX] Global: Fix `ConcurrentModificationException` during `ConsentAwareStorage.dropAll` call. See [#1764](https://github.com/DataDog/dd-sdk-android/pull/1764)
* [BUGFIX] RUM: Convert pending resource to pending error when Resource scope completes with an error. See [#1776](https://github.com/DataDog/dd-sdk-android/pull/1776)
* [BUGFIX] RUM: Fix leak caused by repeated calls to `WeakReference.get()`. See [#1779](https://github.com/DataDog/dd-sdk-android/pull/1779)
* [IMPROVEMENT] Session Replay: Add `resourceId` to `ImageWireframe`. See [#1690](https://github.com/DataDog/dd-sdk-android/pull/1690)
* [IMPROVEMENT] `Logger` integration tests. See [#1735](https://github.com/DataDog/dd-sdk-android/pull/1735)
* [IMPROVEMENT] Add regression test for `Gson#toString` method. See [#1742](https://github.com/DataDog/dd-sdk-android/pull/1742)
* [IMPROVEMENT] Create Stub Core module. See [#1740](https://github.com/DataDog/dd-sdk-android/pull/1740)
* [IMPROVEMENT] Fix flaky test in `WireframeUtils`. See [#1743](https://github.com/DataDog/dd-sdk-android/pull/1743)
* [IMPROVEMENT] Session Replay: Remove `resourceId` field from e2e payloads. See [#1754](https://github.com/DataDog/dd-sdk-android/pull/1754)
* [IMPROVEMENT] RUM: Add session start reason to events. See [#1755](https://github.com/DataDog/dd-sdk-android/pull/1755)
* [IMPROVEMENT] Session Replay: Open text masking classes for extension. See [#1757](https://github.com/DataDog/dd-sdk-android/pull/1757)
* [IMPROVEMENT] Tracing: Update RUM attributes in spans. See [#1758](https://github.com/DataDog/dd-sdk-android/pull/1758)
* [IMPROVEMENT] Add the synchronous equivalent of `readNextBatch` and `confirmBatchRead` in Storage API. See [#1768](https://github.com/DataDog/dd-sdk-android/pull/1768)
* [IMPROVEMENT] Add all Logs Feature integration tests. See [#1769](https://github.com/DataDog/dd-sdk-android/pull/1769)
* [IMPROVEMENT] Remove the v1 data upload components. See [#1774](https://github.com/DataDog/dd-sdk-android/pull/1774)
* [IMPROVEMENT] Add text overflow examples in sample app. See [#1775](https://github.com/DataDog/dd-sdk-android/pull/1775)
* [IMPROVEMENT] Remove data store/upload config from feature configuration. See [#1778](https://github.com/DataDog/dd-sdk-android/pull/1778)
* [MAINTENANCE] Bump dev version to 2.4.0. See [#1738](https://github.com/DataDog/dd-sdk-android/pull/1738)
* [MAINTENANCE] Merge `release/2.3.0` branch into `develop` branch. See [#1739](https://github.com/DataDog/dd-sdk-android/pull/1739)
* [MAINTENANCE] Update RUM schema. See [#1752](https://github.com/DataDog/dd-sdk-android/pull/1752)
* [MAINTENANCE] Remove obsolete integration tests. See [#1770](https://github.com/DataDog/dd-sdk-android/pull/1770)
* [MAINTENANCE] Update obsolete nightly logs test. See [#1771](https://github.com/DataDog/dd-sdk-android/pull/1771)
* [MAINTENANCE] Add artifacts in Gitlab test jobs. See [#1772](https://github.com/DataDog/dd-sdk-android/pull/1772)
* [DOCS] Mention `DatadogTree` in README.md. See [#1744](https://github.com/DataDog/dd-sdk-android/pull/1744)

# 2.3.0 / 2023-11-21

* [FEATURE] Global: Support returning event metadata to the readers. See [#1670](https://github.com/DataDog/dd-sdk-android/pull/1670)
* [FEATURE] Add mapper interface for traversing all children. See [#1684](https://github.com/DataDog/dd-sdk-android/pull/1684)
* [FEATURE] Global: Introduce the `BatchProcessingLevel` API. See [#1686](https://github.com/DataDog/dd-sdk-android/pull/1686)
* [FEATURE] Session Replay: Support `ImageView` views. See [#1677](https://github.com/DataDog/dd-sdk-android/pull/1677)
* [FEATURE] RUM: Create a `SetSyntheticsTestAttribute` event. See [#1714](https://github.com/DataDog/dd-sdk-android/pull/1714)
* [FEATURE] Add synthetics information to the RUM Context. See [#1715](https://github.com/DataDog/dd-sdk-android/pull/1715)
* [FEATURE] Store the synthetics test info in the RUM Context. See [#1716](https://github.com/DataDog/dd-sdk-android/pull/1716)
* [FEATURE] Add synthetics info to RUM Views. See [#1717](https://github.com/DataDog/dd-sdk-android/pull/1717)
* [FEATURE] Add synthetics info to RUM Actions. See [#1718](https://github.com/DataDog/dd-sdk-android/pull/1718)
* [FEATURE] Add synthetics info to RUM Errors. See [#1719](https://github.com/DataDog/dd-sdk-android/pull/1719)
* [FEATURE] Add synthetics info to RUM Resources. See [#1720](https://github.com/DataDog/dd-sdk-android/pull/1720)
* [FEATURE] Add synthetics info to RUM Long Tasks. See [#1721](https://github.com/DataDog/dd-sdk-android/pull/1721)
* [FEATURE] RUM: Track synthetics info from activity extras. See [#1722](https://github.com/DataDog/dd-sdk-android/pull/1722)
* [BUGFIX] Fix the issue of missing cpu/memory info with RUM view events. See [#1693](https://github.com/DataDog/dd-sdk-android/pull/1693)
* [BUGFIX] Fix batch processing level reporting in core configuration telemetry. See [#1698](https://github.com/DataDog/dd-sdk-android/pull/1698)
* [BUGFIX] Unregister RUM monitor when associated RUM feature is stopped. See [#1725](https://github.com/DataDog/dd-sdk-android/pull/1725)
* [BUGFIX] Session Replay: Generate wireframe IDs as 32bit integer. See [#1736](https://github.com/DataDog/dd-sdk-android/pull/1736)
* [IMPROVEMENT] Unit test to confirm Session Replay records order is kept when having same timestamps. See [#1659](https://github.com/DataDog/dd-sdk-android/pull/1659)
* [IMPROVEMENT] Global: Handle Android Strict Mode. See [#1663](https://github.com/DataDog/dd-sdk-android/pull/1663)
* [IMPROVEMENT] Make sure we use try-locks in our NDK signal catcher. See [#1665](https://github.com/DataDog/dd-sdk-android/pull/1665)
* [IMPROVEMENT] RUM: Introduce view event filtering in upload pipeline, remove view event throttling in write pipeline. See [#1678](https://github.com/DataDog/dd-sdk-android/pull/1678)
* [IMPROVEMENT] Make NDK stack traces more standard. See [#1683](https://github.com/DataDog/dd-sdk-android/pull/1683)
* [IMPROVEMENT] Have more consistent results when using the load picture sample screen. See [#1692](https://github.com/DataDog/dd-sdk-android/pull/1692)
* [IMPROVEMENT] Add the `batchProcessingLevel` value to the Configuration Telemetry. See [#1691](https://github.com/DataDog/dd-sdk-android/pull/1691)
* [IMPROVEMENT] Tracing: Update default propagation style from `Datadog` to `Datadog`+`TraceContext`. See [#1696](https://github.com/DataDog/dd-sdk-android/pull/1696)
* [IMPROVEMENT] Tracing: Use `tracestate` header to supply vendor-specific information. See [#1694](https://github.com/DataDog/dd-sdk-android/pull/1694)
* [IMPROVEMENT] Global: Lower the upload frequency and batch size enum values. See [#1733](https://github.com/DataDog/dd-sdk-android/pull/1733)
* [MAINTENANCE] Prepare release 2.2.0. See [#1650](https://github.com/DataDog/dd-sdk-android/pull/1650)
* [MAINTENANCE] Next dev iteration. See [#1651](https://github.com/DataDog/dd-sdk-android/pull/1651)
* [MAINTENANCE] Merge release 2.2.0 branch into develop. See [#1657](https://github.com/DataDog/dd-sdk-android/pull/1657)
* [MAINTENANCE] Fix Session Replay functional tests payloads after develop rollback. See [#1660](https://github.com/DataDog/dd-sdk-android/pull/1660)
* [MAINTENANCE] Create core `testFixtures` source set. See [#1666](https://github.com/DataDog/dd-sdk-android/pull/1666)
* [MAINTENANCE] Refactor shared android library build script. See [#1667](https://github.com/DataDog/dd-sdk-android/pull/1667)
* [MAINTENANCE] Let all modules use the shared fixtures. See [#1668](https://github.com/DataDog/dd-sdk-android/pull/1668)
* [MAINTENANCE] Update testing conventions. See [#1661](https://github.com/DataDog/dd-sdk-android/pull/1661)
* [MAINTENANCE] Disable warning as errors locally. See [#1664](https://github.com/DataDog/dd-sdk-android/pull/1664)
* [MAINTENANCE] Add test pyramid scaffolding. See [#1674](https://github.com/DataDog/dd-sdk-android/pull/1674)
* [MAINTENANCE] Share `RawBatchEvent` forgery for tests between the modules. See [#1680](https://github.com/DataDog/dd-sdk-android/pull/1680)
* [MAINTENANCE] Calculate API coverage. See [#1681](https://github.com/DataDog/dd-sdk-android/pull/1681)
* [MAINTENANCE] Improve `LogsFragment` in sample app. See [#1685](https://github.com/DataDog/dd-sdk-android/pull/1685)
* [MAINTENANCE] Add CI task to update E2E sample app. See [#1688](https://github.com/DataDog/dd-sdk-android/pull/1688)
* [MAINTENANCE] Include `rum-mobile-android` as codeowner. See [#1695](https://github.com/DataDog/dd-sdk-android/pull/1695)
* [MAINTENANCE] Fix flaky test in `WebViewRumEventConsumerTest`. See [#1724](https://github.com/DataDog/dd-sdk-android/pull/1724)
* [MAINTENANCE] Fix flaky test in RumEventDeserializer. See [#1727](https://github.com/DataDog/dd-sdk-android/pull/1727)
* [MAINTENANCE] Fix flaky test in DatadogContextProvider. See [#1726](https://github.com/DataDog/dd-sdk-android/pull/1726)
* [MAINTENANCE] Fix flaky test in `TelemetryEventHandlerTest`. See [#1729](https://github.com/DataDog/dd-sdk-android/pull/1729)
* [MAINTENANCE] Fix flaky test in `BatchFileOrchestratorTest`. See [#1732](https://github.com/DataDog/dd-sdk-android/pull/1732)
* [MAINTENANCE] Reduce noise in logs when building the project. See [#1731](https://github.com/DataDog/dd-sdk-android/pull/1731)
* [MAINTENANCE] Fix flaky test in `MainLooperLongTaskStrategyTest`. See [#1730](https://github.com/DataDog/dd-sdk-android/pull/1730)
* [MAINTENANCE] Create `SDKCore` stub classes. See [#1734](https://github.com/DataDog/dd-sdk-android/pull/1734)

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

This is the first official production version of SDK v2 containing the new architecture for features initialisation and dependencies distribution. See the [migration guide](https://github.com/DataDog/dd-sdk-android/blob/62aac79c3c68c4da02c96ab1071fb5e63f1b8b89/MIGRATION.MD) for details.

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
* [BUGFIX] APM: Fix span with custom `MESSAGE` field [#522](https://github.com/DataDog/dd-sdk-android/issues/522) (Thanks [@JessicaYeh](https://github.com/JessicaYeh))
* [BUGFIX] Logs: Fix tag name in Timber `DatadogTree` [#483](https://github.com/DataDog/dd-sdk-android/issues/483) (Thanks [@cfa-eric](https://github.com/cfa-eric))
* [BUGFIX] RUM: Ensure View linked events count is correct when events are discarded
* [BUGFIX] RUM: Fix Resource network timings
* [BUGFIX] APM: Fix span logs timestamp conversion
* [FEATURE] RUM: Detect Long Tasks (tasks blocking the main thread)
* [FEATURE] RUM: add a callback to enrich RUM Resources created from OkHttp Requests
* [IMPROVEMENT] RUM: Remove the "Application crash detected" prefix and ensure the message is kept
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
