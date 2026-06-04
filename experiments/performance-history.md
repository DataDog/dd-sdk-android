# Performance Improvements History (dd-sdk-android, June 2024 -- June 2026)

## 1. Session Replay -- Most Heavily Optimized Area

Customer-driven (PANA-5027 ticket series) and recurring optimization target.

### Bitmap & Image Optimizations (PANA-5027)

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `d6400cfef` | 2025-12-01 | jonathanmos | Optimize performance with caching -- introduced `Alpha8ResourceCache`, `BitmapConverter`, `BitmapSignatureGenerator` (+925/-225 lines) |
| `3823714fa` | 2025-12-01 | jonathanmos | Cache as UTF-8 |
| `0b7cdaa98` | 2025-12-02 | jonathanmos | Simplify alpha8 cache |
| `d9ee674b9` | 2025-11-26 | jonathanmos | Alpha8 to ARGB8888 greyscale conversion |
| `12c1163e8` | 2025-11-27 | jonathanmos | Remove unnecessary paint flags |

### DrawableUtils Performance

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `8292b7ad0` | 2025-07-31 | jonathanmos | RUM-9016: DrawableUtils performance improvement |
| `2b8f4b0c7` | 2024-12-17 | jonathanmos | Fix cache misses due to wrong drawable -- cache key was wrong, causing repeated re-computation |
| `262fb21e5` | 2026-03-23 | hmorillo | RUM-7740: Extract drawable key generation from ResourcesLRUCache -- eliminated unsafe downcast |
| `0b8255f5b` | 2025-12-08 | jonathanmos | Hardware bitmaps should be copied (correctness fix that avoids later crashes) |

### Bitmap Crash / Safety

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `f9930d60f` | 2024-11-15 | luyi | Fix crash while using recycled bitmap in Session Replay |
| `821b386c7` | 2025-06-16 | luyi | RUM-10346: Avoid copying hardware bitmap in Session Replay |

### Concurrency & Main-Thread Offloading

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `ed951ac9c` | 2025-06-17 | luyi | RUM-10283: Shallow copy node wireframes before iterating in NodeFlattener (ConcurrentModificationException fix) |
| `b7167aa46` | 2025-06-25 | luyi | RUM-10143: Stop posting recorded data item from main thread |
| `d3b817584` | 2025-09-29 | luyi | Mute some Compose Reflection telemetry errors -- reduces noise/overhead |

### SR Telemetry & Skipped Frames

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `315ebd75a` | 2024-09-10 | luyi | RUM-5188: Add session replay skipped frames count in session ended metrics |
| `e57a1d4ba` | 2024-10-02 | luyi | SR dynamic optimisation -- large feature branch for dynamic quality adjustments |

---

## 2. RUM Vitals / Slow Frames / Jank Monitoring

Major multi-month initiative to build, refine, and optimize the slow frames measurement pipeline.

### Slow Frames Collection

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `cb9a41758` | 2025-02-27 | tvaleev | RUM-8654: Supporting slow frames collection (+614 lines, 20 files) |
| `acfca2152` | 2025-03-10 | tvaleev | SlowFrameListener configuration in RUM config |
| `a6c819628` | 2025-03-07 | tvaleev | RUM-8630: Avoiding spikes in short views, support ANR ratio |
| `d18c80c6a` | 2025-03-19 | tvaleev | RUM-9151: Fix missing freeze rate and slow frames rate |
| `f3c24f31a` | 2025-03-17 | tvaleev | RUM-9065: Fix freeze rate and slow frames rate computation |
| `cf702e433` | 2025-04-04 | tvaleev | RUM-9361: Drop missed jank frames from collecting |
| `27ef6f070` | 2026-01-07 | tvaleev | RUM-8949: Enabling UI slow frames by default (GA) |
| `000a351b7` | 2025-11 | aforsythe | Allow JankStats to be explicitly disabled via additionalConfig |

### JankStats Optimization

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `be6f712cb` | 2025-02-26 | tvaleev | RUM-8785: Refactoring JankStats logic -- decoupling from FPS computing (12 files, +430/-176) |
| `a03d8e85a` | 2025-02-28 | tvaleev | RUM-8785: Reducing GC pressure on 'hot' methods in JankStatsActivityLifecycleListener |
| `7b83fcd68` | 2025-06-13 | nogorodnikov | RUM-10347: Avoid polling for RumContext in VitalReaderRunnable |

---

## 3. Core SDK -- Threading & Context Architecture

Foundational rework of how context is managed and work is dispatched.

### Event Processing Thread (major refactor)

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `7956da7a9` | 2025-05-06 | nogorodnikov | RUM-9854: Introduce event processing thread -- new `AsyncEventWriteScope` |
| `cac8f5408` | 2025-04-30 | nogorodnikov | RUM-9746: Resolve file orchestrator from DatadogContext |
| `28116bd4f` | 2025-06-11 | nogorodnikov | RUM-386: Process feature context on the context thread (large refactor, 166+ lines in DatadogCore) |

### Context Performance

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `dcd63fe3b` | 2025-01-20 | xgouchet | Improve updateFeatureContext performances |
| `50a8e2489` | 2025-04-02 | nogorodnikov | Optimize features context reads in TelemetryEventHandler |
| `e53152637` | 2025-06-19 | nogorodnikov | Mark CoreFeature properties participating in DatadogContext as volatile |

### Main-Thread I/O Removal

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `0606a3153` | 2025-08-21 | nogorodnikov | Move OkHttp client initialization to background thread |
| `1947770ff` | 2024-09-25 | mconstantin | RUM-6039: Fix StrictMode warning regarding I/O disk operation on main thread |
| `83d70594d` | 2026-05-05 | hmorillo | RUM-16123: Move broadcast-receiver dispatch off main thread to fix ANRs |
| `06e2d10dc` | 2026-04-27 | agringauz | CalledFromWrongThreadException fix -- ensure RumAppStartupDetector.destroy called on main thread |

---

## 4. Memory Leaks & Corruption

| Commit | Date | Author | Description | Severity |
|--------|------|--------|-------------|----------|
| `c106edcd9` | 2026-04-08 | agringauz | RUM-15390: Fix memory leak on app launch -- refactored RumFirstDrawTimeReporter (18 files) | HIGH |
| `1a28c0b95` | 2026-03-13 | nogorodnikov | RUM-15081: Fix memory corruption in NDK module -- C++ backtrace handler fix | CRITICAL |
| `30caa794a` | 2025-04-22 | mconstantin | RUM-9539: Fix memory leaks in PendingTrace -- massive refactor (808 line Java rewrite) | HIGH |

---

## 5. Concurrency Fixes

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `5a1c79814` | 2026-02-23 | nogorodnikov | RUM-14704: ConcurrentModificationException in RUM pipeline -- EvictingQueue + ViewUIPerformanceReport |
| `d6b56f4ab` | 2026-02-23 | nogorodnikov | Fix more concurrency issues in SlowFramesListener + AggregatingVitalMonitor |
| `2fc54389a` | 2026-02-18 | nogorodnikov | Fix evaluations feature startup deadlock |
| `0558c4c17` | 2026-01-19 | typotter | Fix: use write lock for addListener in FlagsStateManager |
| `9a45d07b9` | 2024-10-04 | mconstantin | Make request retry information thread-safe |
| `2085c284d` | 2024-07-19 | mconstantin | RUM-5447: Fix DnsResolver concurrency issue |

---

## 6. Lazy Evaluation & Allocation Reduction

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `27b870a39` | 2025-05-20 | nogorodnikov | RUM-10040: Lazy capture of DatadogContext at span creation |
| `51ab07453` | 2024-10-31 | nogorodnikov | Lazy RUM raw event creation in event generators |
| `db3e1d6e7` | 2024-12-05 | tvaleev | RUM-6394: Lazy logApiUsage |
| `e65cc7e8c` | 2025-12-19 | typotter | perf(openfeature): Use sentinel to avoid converting default on error |
| `df15ef6d6` | 2024-12-18 | tvaleev | RUM-6286: Replace joinToString with StringBuilder -- with p95 perf benchmarks proving improvement |

---

## 7. Caching Improvements

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `6ee424a60` | 2025-10-06 | jonathanmos | Change exposuresSentCache to LRUCache (Flags module) |
| `a1e7ee15f` | 2025-10-08 | jonathanmos | Set exposure LRU cache to 4MB |
| `43fb203c8` | 2026-02-01 | typotter | Wire up using cached DD context in Flags |
| `3a2ce20a8` | 2026-03-23 | typotter | Fix cold-start stale cache: hasFlags() now waits for persistence load |
| `9a3fe6466` | 2026-02-12 | hmorillo | Store predicate result to prevent counter corruption -- WeakHashMap caching |

---

## 8. Upload & Networking Optimization

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `3dc8afb9b` | 2025-02-27 | jonathanmos | RUM-8038: Reset backoff to minimum on upload success |
| `725ff74e0` | 2025-04-02 | nogorodnikov | Optimize OkHttp configuration telemetry |
| `3e5792cf7` | 2025-08-28 | luyi | RUM-4234: Update Session Replay batch max age to 5h |

---

## 9. Telemetry Overhead Reduction

| Commit | Date | Author | Description |
|--------|------|--------|-------------|
| `5c96c49d5` | 2025-01-23 | xgouchet | Adjust telemetry metrics sampling rates -- reduce self-monitoring overhead |
| `33b065617` | 2024-12-16 | jward | Don't warn about missing view on PerformanceMetric events |

---

## Patterns & Insights

1. **Session Replay is the #1 performance hotspot.** Most performance commits, customer-driven (PANA-5027). Bitmap caching, drawable cache misses, hardware bitmap handling, main-thread offloading, ConcurrentModificationException fixes.

2. **Main-thread contention is a recurring theme.** Multiple independent efforts moved work off the main thread: broadcast receiver dispatch, recorded data posting, OkHttp init, I/O operations, feature context processing. Suggests it creeps back.

3. **Slow frames / JankStats pipeline needed heavy iteration.** 6+ months of bug fixes and computation corrections before GA (Jan 2026). GC pressure on hot methods was explicitly targeted.

4. **Context threading was a foundational bottleneck.** Dedicated event processing thread (RUM-9854) and context thread processing (RUM-386) were large architectural changes.

5. **Memory leaks keep appearing.** Three significant leaks/corruption in separate modules: RUM app launch, NDK crash handler, PendingTrace.

6. **Concurrency is a recurring pain point.** ConcurrentModificationException, deadlocks, race conditions, missing volatile annotations across RUM, Flags, core.

7. **Customer tickets (PANA-\*)** drove the most impactful SR performance work.

8. **Allocation reduction matters.** The `joinToString` -> `StringBuilder` fix (RUM-6286) came with p95 benchmarks proving improvement, showing these micro-optimizations have measurable impact.

## Mapping to Benchmark Ideas

These findings reinforce the following items from `experiments/benchmark-ideas.md`:

| History Pattern | Benchmark Idea |
|----------------|----------------|
| SR bitmap/drawable path (PANA-5027) | 5.1 SnapshotProducer, 5.6 Image resource capture |
| Main thread work during scroll | 9.4 Heavy RecyclerView scroll, 6.4 GesturesListener |
| JankStats GC pressure | 8.3 Allocation rate in hot paths |
| Context reads/updates | 2.1 RUM event round-trip, 2.5 Scope tree traversal |
| Memory leaks | 8.1 Steady-state memory overhead |
| Serialization allocations | 2.2 ViewEvent serialization, 7.1 Log serialization |
| Cold start / init | 1.1 Datadog.initialize() wall time, 9.3 Feature permutations |
| OkHttp interceptor overhead | 6.1 TracingInterceptor, 6.2 DatadogInterceptor |
