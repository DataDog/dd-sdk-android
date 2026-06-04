# Benchmark Ideas for dd-sdk-android

## Current State

- **Macrobenchmark**: One scenario (`SessionReplayRumAutoBenchmark`) measuring frame timing with SR + RUM on View-based (Fragment) UI with scrolling.
- **Microbenchmark**: Empty (placeholder `ExampleBenchmark` that sorts integers).

---

## Category 1: SDK Initialization (Cold Start Impact)

| # | Benchmark | Type | Metric | Why It Matters | Setup |
|---|-----------|------|--------|---------------|-------|
| 1.1 | **Datadog.initialize() wall time** | Macro | Startup time (ms), `TimeToInitialDisplayMetric` | SDK init runs synchronously on `Application.onCreate()`. Any regression directly increases app cold-start time. | Macrobenchmark with `StartupMode.COLD`, measure `StartupTimingMetric` and a custom `TraceSectionMetric` around `Datadog.initialize`. Compare baseline (no SDK) vs. SDK initialized. |
| 1.2 | **Feature registration overhead** | Micro | Time per `registerFeature()` call | Each feature (RUM, Logs, Trace, SR) registers independently. Customers enable 2-4 features; cumulative cost matters. | Microbenchmark calling `Rum.enable()`, `Logs.enable()`, `Trace.enable()`, `SessionReplay.enable()` individually against a stub/real `SdkCore`. |
| 1.3 | **CoreFeature.initialize internals** | Micro | Time (ns) | `CoreFeature` sets up OkHttpClient (TLS/FIPS config), NTP sync, executor pools, persistence directories. The lazy `OkHttpClient` with `ConnectionSpec.RESTRICTED_TLS` is particularly heavy. | Microbenchmark isolating `CoreFeature.initialize()` with a real `Context`. |

## Category 2: Event Processing Pipeline (RUM)

| # | Benchmark | Type | Metric | Why It Matters | Setup |
|---|-----------|------|--------|---------------|-------|
| 2.1 | **RUM event round-trip: handleEvent -> serialize -> persist** | Micro | Time (ns), allocations | Every user interaction (tap, scroll, navigation) flows through `DatadogRumMonitor.handleEvent()` -> scope tree traversal -> `RumEventSerializer.serialize()` -> `FileEventBatchWriter.write()`. This is the hottest path in RUM. | Microbenchmark constructing a `RumRawEvent.StartView`, processing it through `RumApplicationScope.handleEvent()` with a real writer backed by temp files. |
| 2.2 | **RumEventSerializer for ViewEvent** | Micro | Time (ns), output size | `ViewEvent` is the most frequently serialized model. Serialization involves `toJson()`, attribute validation via `DatadogDataConstraints`, `safeMapValuesToJson`, and JSON string construction. | Microbenchmark calling `RumEventSerializer.serialize(viewEvent)` with varying attribute counts (0, 10, 50 custom attributes). |
| 2.3 | **RumEventSerializer for all event types** | Micro | Time (ns) per type | Covers `ActionEvent`, `ResourceEvent`, `ErrorEvent`, `LongTaskEvent`. Each follows the same sanitize-then-serialize pattern but with different model sizes. | Parameterized microbenchmark over each event type with representative payloads. |
| 2.4 | **DatadogDataConstraints.validateAttributes** | Micro | Time (ns) | Called on every single RUM event serialization (user attrs, context attrs, account attrs). Iterates all keys, validates lengths, checks reserved keys. | Microbenchmark with 10/50/128 attributes of varying key/value sizes. |
| 2.5 | **RUM scope tree traversal depth** | Micro | Time (ns) | `handleEvent` walks Application -> Session -> View -> Action/Resource/Error scopes. A deep scope tree with many active resources can be expensive. | Microbenchmark with a RumViewScope handling events while 10/50 concurrent resources are active. |

## Category 3: Persistence Layer

| # | Benchmark | Type | Metric | Why It Matters | Setup |
|---|-----------|------|--------|---------------|-------|
| 3.1 | **PlainBatchFileReaderWriter.writeData (TLV write)** | Micro | Time (ns), I/O bytes | Every persisted event goes through TLV encoding + `RandomAccessFile` with file locking. This is the I/O bottleneck. | Microbenchmark writing events of 500B / 5KB / 50KB to batch files, measuring write latency. |
| 3.2 | **PlainBatchFileReaderWriter.readData (TLV read)** | Micro | Time (ns) | Read path is used by the upload pipeline. Batch files can contain 10-500 events. | Microbenchmark reading batch files with 10/100/500 pre-written events. |
| 3.3 | **FileOrchestrator.getWritableFile** | Micro | Time (ns) | Orchestrator checks file sizes, counts, creates new files. Called on every write. | Microbenchmark in a directory with varying numbers of existing batch files (0, 10, 50). |
| 3.4 | **ConsentAwareStorage.getEventWriteScope** | Micro | Time (ns) | Creates `FileEventBatchWriter` instances per write scope. Involves orchestrator resolution based on consent state. | Microbenchmark calling `getEventWriteScope()` repeatedly under GRANTED consent. |

## Category 4: Upload Pipeline

| # | Benchmark | Type | Metric | Why It Matters | Setup |
|---|-----------|------|--------|---------------|-------|
| 4.1 | **Gzip compression of batch payloads** | Micro | Time (ns), compression ratio | `GzipRequestInterceptor` compresses every upload. Large SR batches can be megabytes. | Microbenchmark compressing representative payloads: small RUM batch (5KB), large log batch (500KB), SR segment (2MB). |
| 4.2 | **RequestFactory.create (batch assembly)** | Micro | Time (ns) | Each feature has a `RequestFactory` that assembles the HTTP request from batch events. Includes JSON concatenation, header construction. | Microbenchmark for `RumRequestFactory`, `LogsRequestFactory`, `TracesRequestFactory` with representative batch sizes. |
| 4.3 | **DataUploadRunnable full cycle** | Micro | Time (ns) | The upload loop: read batch -> create request -> execute upload -> confirm/delete. Test with a mock server to isolate SDK overhead from network. | Microbenchmark with `MockWebServer` measuring end-to-end upload cycle time. |

## Category 5: Session Replay

| # | Benchmark | Type | Metric | Why It Matters | Setup |
|---|-----------|------|--------|---------------|-------|
| 5.1 | **SnapshotProducer.produce (View tree traversal)** | Micro | Time (ns) on UI thread | Runs on the **main thread** (`@UiThread`). Traverses the entire view hierarchy, maps each view to wireframes. Directly causes jank if slow. | Microbenchmark with synthetic view hierarchies of varying depth (5/15/30 levels) and breadth (10/50/100 views). Use `TraceSectionMetric` already in place. |
| 5.2 | **RootSemanticsNodeMapper (Compose SR traversal)** | Micro | Time (ns) on UI thread | Compose SR traverses the semantics tree. With complex Compose UIs (lazy lists, nested layouts), this can be expensive. | Microbenchmark with a Compose test rule, creating a screen with 50-200 semantics nodes. |
| 5.3 | **Frame timing with SR + Compose scrollable content** | Macro | frameDurationCpuMs, frameOverrunMs | The existing benchmark uses View-based (Fragment) UI. Compose SR uses a different traversal path (`dd-sdk-android-session-replay-compose`) that exercises `LayoutNodeUtils`, `RootSemanticsNodeMapper` etc. A `SessionReplayCompose` scenario enum already exists in the sample app. | New Macrobenchmark navigating to a fully Compose-based scrollable screen with SR enabled. |
| 5.4 | **SR RecordedDataQueueHandler throughput** | Micro | Items/sec, queue depth | The queue bridges main thread (snapshot production) and background thread (processing). If processing is slow, the queue backs up and items expire after 1 second. | Microbenchmark producing N snapshot items and measuring drain time. |
| 5.5 | **SegmentRequestBodyFactory.create (SR segment serialization + compression)** | Micro | Time (ns) | SR segments are large (each contains wireframes for the full screen). Serialization (`MobileSegment.toJson()`) + gzip compression is expensive. | Microbenchmark with representative `MobileSegment` objects of varying complexity. |
| 5.6 | **Image resource capture and compression** | Micro | Time (ns), memory | SR captures drawable resources, compresses them. `Alpha8ResourceCache`, `ImageCompression` are in the hot path during scroll. | Microbenchmark capturing and compressing bitmaps of various sizes (100x100, 500x500, 1080x1920). |

## Category 6: Integrations (OkHttp, Compose)

| # | Benchmark | Type | Metric | Why It Matters | Setup |
|---|-----------|------|--------|---------------|-------|
| 6.1 | **TracingInterceptor overhead per request** | Micro | Time added (ns) per HTTP call | The OkHttp `TracingInterceptor` creates spans, injects tracing headers (Datadog, W3C TraceContext, B3), and reports to RUM. Added to every HTTP request. | Microbenchmark using `MockWebServer`, comparing request time with/without the interceptor. Measure the `intercept()` method directly. |
| 6.2 | **DatadogInterceptor (combined RUM + APM)** | Micro | Time added (ns) | Combines tracing + RUM resource tracking. Most customers use this. | Similar to 6.1 but with `DatadogInterceptor`. |
| 6.3 | **DatadogEventListener timing overhead** | Micro | Time (ns) per callback | `DatadogEventListener` measures DNS, connect, TLS, request/response body timings. Each callback fires on OkHttp's thread. | Microbenchmark simulating the full lifecycle of event listener callbacks. |
| 6.4 | **GesturesListener.onSingleTapUp (view tree walk)** | Micro | Time (ns) | On every tap, `GesturesListener` walks the view hierarchy to find the tapped target, resolves attributes. Runs on the UI thread. | Microbenchmark with synthetic view hierarchies of varying depth/breadth. |
| 6.5 | **Compose action tracking** | Macro | Frame timing during interaction | Compose integration tracks gestures through the composition. Could add overhead during rapid interactions. | Macrobenchmark with rapid tap/scroll sequences on a Compose screen with tracking enabled. |

## Category 7: Logging and Tracing

| # | Benchmark | Type | Metric | Why It Matters | Setup |
|---|-----------|------|--------|---------------|-------|
| 7.1 | **LogEventSerializer.serialize** | Micro | Time (ns) | Called for every log. Includes tag validation (`validateTags` splits/joins strings), attribute validation, JSON serialization. | Microbenchmark with logs containing 5/20/50 attributes and varying tag counts. |
| 7.2 | **DatadogLogHandler full pipeline** | Micro | Time (ns) | End-to-end: `LogGenerator.generateLog()` -> `LogEventMapperWrapper` -> `LogEventSerializer` -> persist. | Microbenchmark calling `Logger.d("message", attributes)` with varying complexity. |
| 7.3 | **Log throughput under heavy load** | Micro | Logs/sec, dropped count | Customers sometimes log in tight loops (e.g., per-frame logging in games). BackPressureStrategy should kick in gracefully. | Microbenchmark firing 10K logs/sec and measuring how many get persisted vs. dropped, and the queue drain time. |
| 7.4 | **Span serialization** | Micro | Time (ns) | Trace span serialization (via `dd-trace` internals) to JSON. | Microbenchmark serializing spans with 5/20 tags. |

## Category 8: Memory and Threading

| # | Benchmark | Type | Metric | Why It Matters | Setup |
|---|-----------|------|--------|---------------|-------|
| 8.1 | **Steady-state memory overhead** | Macro | `MemoryUsageMetric(Mode.Max)`, heap delta | Customers care about the SDK's resident memory footprint. SR is especially memory-heavy (caches, bitmaps). | Macrobenchmark comparing `MemoryUsageMetric` for: (a) no SDK, (b) RUM only, (c) RUM+Logs+Trace, (d) RUM+SR. |
| 8.2 | **Thread count and contention** | Macro | Thread count, CPU time | The SDK creates multiple executor services (per-feature writers, upload schedulers, SR queue, NTP sync). Too many threads can starve the app. | Macrobenchmark logging thread count at steady state. Could also use `TraceSectionMetric` on contended `synchronized` blocks. |
| 8.3 | **Allocation rate in hot paths** | Micro | Allocations/iteration | Excessive allocations in `handleEvent`, serializers, or SR traversal cause GC pauses which cause jank. | Use Jetpack Benchmark's allocation tracking (or `AllocationCountRule`) on: `handleEvent`, `RumEventSerializer.serialize`, `SnapshotProducer.produce`. |

## Category 9: Macro-Level App Impact Scenarios

| # | Benchmark | Type | Metric | Why It Matters | Setup |
|---|-----------|------|--------|---------------|-------|
| 9.1 | **Baseline app (no SDK) vs. full SDK** | Macro | Startup, frame timing, memory | The most important benchmark: what is the total overhead of the SDK on a real app? | Macrobenchmark with build variants: no-op SDK stub vs. full SDK with RUM+Logs+Trace+SR. Same UI interactions. |
| 9.2 | **Frame timing: RUM only vs. RUM + Session Replay** | Macro | frameDurationCpuMs, frameOverrunMs | Isolates the incremental cost of Session Replay on frame rendering. Already partially covered but should be formalized as a comparison. | Two benchmark runs on the same app/scenario, one with SR disabled, one enabled. |
| 9.3 | **Cold start: feature permutations** | Macro | `StartupTimingMetric` | Startup time with different combinations: Core only, +RUM, +Logs, +Trace, +SR. | Macrobenchmark with different intent extras selecting feature sets, or separate build flavors. |
| 9.4 | **Heavy RecyclerView scroll with all features** | Macro | Frame timing, frameOverrunMs | RecyclerView scroll is the most jank-sensitive scenario. RUM gesture tracking + SR snapshot traversal both run on the UI thread during scroll. | Macrobenchmark scrolling a RecyclerView with 100+ heterogeneous items (text, images, nested layouts) with RUM+SR. |
| 9.5 | **Network-heavy scenario** | Macro | Frame timing, upload metrics | App making many concurrent HTTP requests (e.g., image-heavy feed). OkHttp interceptor fires for each request, generating RUM resources and trace spans. | Macrobenchmark loading a screen that triggers 20-50 network requests in parallel. |

---

## Priority Ranking

**Highest priority** (most likely to catch regressions, highest customer impact):

1. **5.1** SnapshotProducer (runs on UI thread, directly causes jank)
2. **9.1** Baseline vs. full SDK (the "is the SDK making my app slower?" question)
3. **2.1** RUM event round-trip (hottest path, every interaction)
4. **1.1** SDK initialization time (cold start impact)
5. **6.1** TracingInterceptor per-request overhead (every network call)
6. **3.1** TLV batch file write (I/O on every event)
7. **5.3** SR + Compose scrollable content frame timing (growing Compose adoption, newer code)
8. **8.1** Memory overhead (SDK memory budget)

**Medium priority** (important for specific features):

9. **2.2** ViewEvent serialization
10. **4.1** Gzip compression
11. **5.5** SR segment serialization
12. **7.1** Log serialization
13. **6.4** GesturesListener view tree walk
14. **8.3** Allocation rate in hot paths

**Lower priority** (useful for optimization, less likely to regress visibly):

15. Everything else (upload pipeline details, feature registration, thread count, etc.)
