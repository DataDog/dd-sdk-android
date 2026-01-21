# Architecture

**Analysis Date:** 2026-01-21

## Pattern Overview

**Overall:** Modular Feature-Based Architecture with Plugin Pattern

**Key Characteristics:**
- Multi-instance SDK support through `SdkCoreRegistry`
- Feature plugin system where each data collection feature (Logs, RUM, Tracing) registers independently
- Event-driven pipeline: capture → serialize → persist (file-based) → upload (async scheduled)
- Separation between public API interfaces (in `api/` modules) and internal implementations (in `internal/`)
- Thread-safe concurrent patterns with atomic counters and concurrent collections for lifecycle management

## Layers

**Initialization Layer:**
- Purpose: Bootstrap SDK instances, register features, set configuration
- Location: `com.datadog.android.Datadog` (entry point), `dd-sdk-android-core/src/main/kotlin/com/datadog/android/`
- Contains: `Datadog` object (singleton for SDK initialization), `Configuration` classes, `SdkCoreRegistry`
- Depends on: Nothing directly; provides entry points for all other layers
- Used by: Application initialization code

**Core Infrastructure Layer:**
- Purpose: Provide shared services used by all features (context management, persistence, networking, lifecycle monitoring)
- Location: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/`
- Contains:
  - `DatadogCore` - Main SDK instance implementing `SdkCore` interface
  - `CoreFeature` - Built-in core feature managing context, configuration, lifecycle
  - `ContextProvider` and friends - Access to `DatadogContext` (device info, user info, network state)
  - Lifecycle monitoring (`ProcessLifecycleMonitor`) - Tracks app foreground/background state
  - Persistence layer (`persistence/` subdirectory) - File storage abstractions
  - Upload coordination (`data/upload/`) - Scheduled data transmission to Datadog intake
  - Time management - Server/device time synchronization
  - Network info management - WiFi/cellular state tracking
- Depends on: Android Framework, configuration
- Used by: All feature modules

**Feature Layer:**
- Purpose: Implement specific telemetry collection (Logs, RUM, Traces, Session Replay, Flags, etc.)
- Location: `features/dd-sdk-android-*` modules
- Contains:
  - Feature implementation (`*Feature.kt`) implementing `StorageBackedFeature`
  - Domain models (`internal/domain/`) - Event construction logic
  - Monitoring classes (e.g., `DatadogRumMonitor` for RUM)
  - Tracking strategies (e.g., `UserActionTrackingStrategy`)
  - Data writers (`*DataWriter.kt`) implementing `DataWriter<T>`
  - Request factories (`internal/net/*RequestFactory.kt`) implementing `RequestFactory`
- Examples:
  - `features/dd-sdk-android-rum/` - Real User Monitoring
  - `features/dd-sdk-android-logs/` - Log collection
  - `features/dd-sdk-android-trace/` - Distributed tracing
  - `features/dd-sdk-android-session-replay/` - Screen recording
- Depends on: Core infrastructure layer, shared API interfaces
- Used by: Application code via global monitors or feature-specific public APIs

**Public API Layer:**
- Purpose: Define contracts between SDK and application code
- Location: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/api/`
- Contains:
  - `SdkCore` interface - Main SDK instance contract
  - `Feature` interface - Plugin contract for features
  - `DataWriter<T>` interface - Event writing abstraction
  - `RequestFactory` interface - HTTP request generation
  - Context interfaces (`DatadogContext`, `UserInfo`, `NetworkInfo`, etc.)
  - Storage interfaces (`EventBatchWriter`, `RawBatchEvent`)
- Used by: Feature implementations, external integrations, application code

**Integration Layer:**
- Purpose: Bridge third-party libraries with SDK
- Location: `integrations/dd-sdk-android-*` modules
- Contains:
  - OkHttp interceptors (`dd-sdk-android-okhttp`) - Network traffic monitoring
  - Image library adapters (Coil, Glide, Fresco) - Image loading metrics
  - GraphQL client integration (Apollo) - Query tracking
  - Async framework support (RxJava, Coroutines)
  - Logging framework adapters (Timber)
- Depends on: Core infrastructure, third-party libraries, public APIs
- Used by: Applications using supported libraries

**Persistence Layer:**
- Purpose: Store events durably on device before upload
- Location: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/persistence/`
- Contains:
  - File-based storage strategies (`file/` subdirectory)
  - Batch management (`file/batch/`, `file/single/`)
  - Advanced storage (`file/advanced/`)
  - DataStore (SharedPreferences alternative) storage (`datastore/`)
  - TLV format for binary serialization (`tlvformat/`)
- Depends on: Android Framework (File I/O), Android SharedPreferences
- Used by: Feature data writers, upload scheduling

**Upload & Networking Layer:**
- Purpose: Batch, compress, and upload events to Datadog intake
- Location: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/data/upload/`
- Contains:
  - `DataUploader` interface and implementations
  - `UploadScheduler` - Decides when to attempt uploads
  - `DataFlusher` - Performs actual HTTP upload with gzip compression
  - `UploadRunnable` - Scheduled task runner
  - OkHttp interceptors for request/response handling
- Depends on: Core infrastructure, OkHttp, persistence layer
- Used by: Core feature during SDK lifecycle

## Data Flow

**Event Capture and Storage:**

1. Application calls feature API (e.g., `GlobalRumMonitor.addAction()` or `Logger.info()`)
2. Feature monitor/handler receives event
3. Constructs domain object (e.g., `RumRawEvent`, log model)
4. Calls `DataWriter.write(EventBatchWriter, event, eventType)`
5. Writer serializes event (via `EventMapper` if provided)
6. Writer writes bytes to `EventBatchWriter` (storage abstraction)
7. `EventBatchWriter` persists to file system via persistence layer
8. Event file stored in feature-specific directory under app cache/files

**Upload Scheduling and Execution:**

1. Persistence layer notifies `UploadScheduler` when batch is ready
2. `UploadScheduler.schedule()` posts `UploadRunnable` to executor (respecting frequency settings)
3. On upload attempt:
   - Executor calls `DataFlusher.flush()`
   - Reads batches from persistence layer
   - Compresses with gzip
   - Creates HTTP request via feature's `RequestFactory`
   - OkHttp interceptors add headers, instrumentation
   - Sends to Datadog intake endpoint
4. On success: batches deleted from storage
5. On failure: retry scheduled based on `UploadStatus`

**Feature Registration:**

1. Application calls `Datadog.initialize()`
2. Creates `DatadogCore` instance, stores in `SdkCoreRegistry`
3. Feature module calls `sdkCore.registerFeature(feature)`
4. `DatadogCore` stores feature in `features` map by name
5. Calls `feature.onInitialize(context)` - feature sets up tracking
6. Feature sets up listeners, executors, request factories, data writers
7. On `Datadog.stop()`, features unregistered and `onStop()` called for cleanup

**Event-Context Binding:**

1. Core maintains `DatadogContext` with current device/user state
2. Features receive context via `FeatureContextUpdateReceiver` when context changes
3. Context includes: device info, user info, network info, time info
4. When feature writes event, `DatadogContext` is current snapshot at that moment
5. Allows events to be enriched with metadata without duplication

## Key Abstractions

**Feature Plugin:**
- Purpose: Represents a telemetry collection module
- Defines: `Feature` interface with `onInitialize(context)` and `onStop()`
- Examples: `RumFeature`, `LogsFeature`, `TraceFeature`
- Pattern: Registry keeps features by name, lifecycle managed centrally

**DataWriter (Event Persistence):**
- Purpose: Convert in-memory events to persisted format
- Method: `write(EventBatchWriter, element, eventType): Boolean`
- Implementations: `RumDataWriter`, `LogsDataWriter`, trace writers
- Pattern: Type-safe, feature-specific, handles serialization and storage

**RequestFactory (HTTP Generation):**
- Purpose: Create Datadog intake requests from batches
- Method: `create(RawBatchEvent, context, batchId): HttpRequest`
- Implementations: `RumRequestFactory`, `LogsRequestFactory`
- Pattern: Encapsulates endpoint URLs, headers, compression

**FeatureScope (Event Context):**
- Purpose: Scoped context for hierarchy of RUM events
- Examples: `RumApplicationScope` (session level), `RumSessionScope`, view/resource/action scopes
- Pattern: Nested scopes manage state transitions, parent-child relationships

**UploadScheduler (Async Coordination):**
- Purpose: Decide upload timing based on network, time, batch size
- Strategies: Immediate on cellular, delayed on WiFi, periodic backoff
- Pattern: Pluggable strategies via `UploadSchedulerStrategy`

**RumMonitor (Event Collection):**
- Purpose: Public API for application to report RUM data
- Methods: `addAction()`, `addError()`, `addResource()`, `startView()`, etc.
- Implementation: `DatadogRumMonitor` - delegates to scopes, applies sampling
- Pattern: Global monitor accessible via `GlobalRumMonitor`

## Entry Points

**Application Initialization:**
- Location: `com.datadog.android.Datadog.initialize()`
- Triggers: Called once in Application or Activity onCreate
- Responsibilities:
  - Create SDK instance with configuration
  - Register core feature
  - Start lifecycle monitoring
  - Ready SDK for feature registration

**Feature Registration:**
- Location: Implicit within feature modules' `onInitialize()`
- Triggers: Called by core during feature registration
- Responsibilities:
  - Set up feature-specific tracking (e.g., lifecycle callbacks, interceptors)
  - Create data writers, request factories
  - Start collection if enabled

**Public Feature APIs:**
- Location: Feature-specific global accessors (e.g., `GlobalRumMonitor.get()`, `Logger`)
- Triggers: Called by application code throughout app lifecycle
- Responsibilities: Route events to appropriate monitor/handler

**SDK Shutdown:**
- Location: `Datadog.stopAll()` or per-instance `sdkCore.stop()`
- Triggers: Manual call on app shutdown or via lifecycle observer
- Responsibilities:
  - Stop feature collection
  - Flush pending batches
  - Clean up resources

## Error Handling

**Strategy:** Defensive fail-safe approach with no-op fallbacks

**Patterns:**
- Invalid configuration: Log warning, return null from `Datadog.initialize()`
- Feature initialization failure: Log error, feature remains no-op
- Serialization errors: Event skipped, warning logged, processing continues
- Network upload failure: Retry scheduled, events retained
- Thread safety violations: Atomic wrappers prevent race conditions, synchronized blocks for critical sections
- Low storage: Older batches deleted when limit reached

**Logger Layer:**
- `InternalLogger` interface for SDK logging
- `SdkInternalLogger` implementation routes to Android Log when debug enabled
- `unboundInternalLogger` for early initialization (before context ready)

## Cross-Cutting Concerns

**Logging:**
- SDK uses `InternalLogger` for diagnostic logging
- Routed to `android.util.Log` when `debugEnabled=true` in config
- Internal logs only; does not interfere with application logs

**Validation:**
- Input validation at feature boundaries (user IDs, event values)
- Constraints checked: string length limits, nesting depth limits
- Sanitization of special characters in tags and attributes

**Authentication:**
- Client token provided at initialization
- Used in all requests to Datadog intake
- No per-feature authentication; shared across all features

**Thread Safety:**
- Process lifecycle: atomic counters (`AtomicInteger`, `AtomicBoolean`)
- Feature map: `ConcurrentHashMap`
- Event writing: synchronized access to batch writers
- Executor services: `ScheduledExecutorService` for time-based uploads

**Sampling:**
- Session-level sampling (all/partial/none) via `sampleRate`
- Applied at event writing time via `RumDataWriter`
- Sampling decision persists for session duration

**Privacy:**
- Tracking consent: `PENDING`, `GRANTED`, `NOT_GRANTED`
- Core tracks consent state, features gate data collection
- Privacy model: Datadog Core extends privacy module with filters

---

*Architecture analysis: 2026-01-21*
