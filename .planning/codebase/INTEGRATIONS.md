# External Integrations

**Analysis Date:** 2026-01-21

## APIs & External Services

**Datadog Intake Services:**
- Datadog Logs Collection - Ingests application logs
  - SDK/Client: Built-in (no external SDK required)
  - Auth: Client token (provided at initialization)
  - Sites supported: US1, US3, US5, EU1, AP1, AP2, US1_FED, STAGING

- Datadog RUM (Real User Monitoring) - Ingests RUM events (views, actions, resources, errors, long tasks)
  - SDK/Client: Built-in feature in `features:dd-sdk-android-rum`
  - Auth: Client token
  - Supports session replay and debug widgets

- Datadog APM (Application Performance Monitoring) / Traces - Ingests distributed traces
  - SDK/Client: Built-in feature in `features:dd-sdk-android-trace`
  - Auth: Client token
  - Supports OpenTelemetry (via `features:dd-sdk-android-trace-otel`)

**Third-Party HTTP Clients (Instrumentation):**
- OkHttp 4.12.0 - Interceptor-based instrumentation via `integrations:dd-sdk-android-okhttp`
  - Used for RUM resource tracking and APM span creation
  - Supports custom host headers via `DefaultFirstPartyHostHeaderTypeResolver`

- Cronet 141.7340.3 (via Play Services 18.1.1) - Support via `integrations:dd-sdk-android-cronet`
  - Instrumentation wrapper for Chromium network stack

**Image Loading Libraries (Instrumentation):**
- Coil 1.0.0 - Instrumentation via `integrations:dd-sdk-android-coil`
- Coil3 3.0.4 - Instrumentation via `integrations:dd-sdk-android-coil3`
  - OkHttp network integration for network metric tracking

- Fresco 2.3.0 - Instrumentation via `integrations:dd-sdk-android-fresco`
  - With OkHttp3 backend for network request tracking

- Glide 4.11.0 - Instrumentation via `integrations:dd-sdk-android-glide`
  - With OkHttp3 integration module

**GraphQL:**
- Apollo (GraphQL) 4.3.3 - Instrumentation via `integrations:dd-sdk-android-apollo`
  - OkHttp client integration for query/mutation tracking

**Database/Storage:**
- SQLDelight 1.5.5 - Instrumentation via `integrations:dd-sdk-android-sqldelight`
  - SQL query and execution time tracking

**Async/Reactive:**
- RxJava 3.0.0 - Instrumentation via `integrations:dd-sdk-android-rx`
  - Observable and subscription tracking via `dd-sdk-android-rx`

- Kotlin Coroutines 1.4.2 - Instrumentation via:
  - `integrations:dd-sdk-android-rum-coroutines` - RUM correlation with coroutines
  - `integrations:dd-sdk-android-trace-coroutines` - Trace propagation in coroutines

**UI Frameworks:**
- Jetpack Compose - Instrumentation via `integrations:dd-sdk-android-compose`
- Android TV Leanback 1.0.0 - Support via `integrations:dd-sdk-android-tv`

**Logging:**
- Timber 5.0.1 - Log forwarding via `integrations:dd-sdk-android-timber`
  - Automatic log capture from Timber trees

**WebView:**
- Android WebView - Instrumentation via `features:dd-sdk-android-webview`

**Media:**
- ExoPlayer 2.19.1 - Support libraries (may have integration in samples)
- NewPipe Extractor v0.24.6 - Used in sample projects

## Data Storage

**Databases:**
- SQLite (implicit via Android, accessible via SQLDelight integration)
  - Client: Internal SQLite storage for SDK data
  - Purpose: Local event persistence before upload

- In-Memory State
  - Used for active session tracking and RUM context management

**File Storage:**
- Local filesystem - SDK event batches stored locally before upload
  - Path configuration via `PersistenceStrategy.Factory`
  - Encryption support via `Encryption` interface (customizable)

**Caching:**
- In-memory caching for:
  - Session state
  - View stack for RUM hierarchy
  - User context
  - Network connection state (via `CallbackNetworkInfoProvider` and `BroadcastReceiverNetworkInfoProvider`)

## Authentication & Identity

**Auth Provider:**
- Custom token-based authentication (no OAuth/OIDC)
  - Implementation: Client token provided at SDK initialization in `Configuration.Builder`
  - Environment variable: `CENTRAL_PUBLISHER_USERNAME`, `CENTRAL_PUBLISHER_PASSWORD` (for CI publishing, not runtime)

**User Identification:**
- Manual via `Datadog.setUserInfo()` API
  - Supports user ID, email, name, and custom attributes
  - Attributes stored in context and attached to all telemetry

## Monitoring & Observability

**Error Tracking:**
- Built-in to RUM/Logs features
- Captures uncaught exceptions, ANR events, and custom errors
- NDK support via `features:dd-sdk-android-ndk` for native crashes

**Logs:**
- Multiple output targets:
  - Datadog intake endpoint (default)
  - Local file storage with batching
  - Custom persistence via `PersistenceStrategy`

**Telemetry:**
- Internal telemetry collection via `BatchMetricsDispatcher` and `MethodCalledTelemetry`
- Tracks SDK performance metrics and method invocation counts

## CI/CD & Deployment

**Hosting:**
- Maven Central (via Sonatype OSSRH) - Primary publishing target
- JitPack support enabled via repository configuration

**CI Pipeline:**
- GitHub Actions (inferred from repository structure)
- Publishing via environment variables:
  - `CENTRAL_PUBLISHER_USERNAME` - Sonatype username
  - `CENTRAL_PUBLISHER_PASSWORD` - Sonatype password
  - Nexus staging API endpoint: `https://ossrh-staging-api.central.sonatype.com/service/local/`

**Build Artifacts:**
- Multiple library variants:
  - Core library: `dd-sdk-android-core`
  - Features: logs, RUM, traces, session replay, NDK, webview, flags
  - Integrations: OkHttp, Apollo, image loaders, coroutines, etc.
  - Debug utilities: RUM debug widget

## Environment Configuration

**Required env vars:**
- `ANDROID_SDK_ROOT` - Android SDK location (for builds if `local.properties` unavailable)

**Optional env vars:**
- `CENTRAL_PUBLISHER_USERNAME` - Maven Central publishing
- `CENTRAL_PUBLISHER_PASSWORD` - Maven Central publishing

**Secrets location:**
- `local.properties` - SDK directory path (not committed)
- CI/CD environment variables - Publishing credentials

## Webhooks & Callbacks

**Incoming:**
- No external webhook handlers
- Network callbacks via `NetworkInfoProvider` interface for internal state changes

**Outgoing:**
- HTTP POST requests to Datadog intake endpoints based on `DatadogSite` configuration
- Requests include: logs, RUM events, APM traces, custom metrics
- Batched uploads controlled by `BatchSize`, `UploadFrequency`, and `UploadSchedulerStrategy`

**Network Configuration:**
- Proxy support via `Proxy` and `Authenticator` in `Configuration.Core`
- First-party host detection via `DefaultFirstPartyHostHeaderTypeResolver`
- Clear-text HTTP support (configurable via `needsClearTextHttp` in core config)

## Data Upload

**Batch Processing:**
- Batching strategy: `BatchSize` (SMALL, MEDIUM, LARGE) and `UploadFrequency` (FREQUENT, AVERAGE, RARE)
- Batch level processing via `BatchProcessingLevel`
- Backpressure mitigation via `BackPressureStrategy` and `BackPressureMitigation`
- Scheduler strategy via `UploadSchedulerStrategy` (WorkManager-based by default)

**Compression & Encryption:**
- JSON serialization with GSON
- Optional encryption via `Encryption` interface
- Custom persistence strategies supported via `PersistenceStrategy.Factory`

---

*Integration audit: 2026-01-21*
