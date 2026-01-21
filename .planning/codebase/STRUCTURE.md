# Codebase Structure

**Analysis Date:** 2026-01-21

## Directory Layout

```
dd-sdk-android-3/
├── dd-sdk-android-core/         # Core SDK functionality (SdkCore, context, persistence, upload)
│   ├── src/main/kotlin/com/datadog/android/
│   │   ├── api/                 # Public interfaces (SdkCore, Feature, DataWriter, RequestFactory)
│   │   ├── core/                # Public SDK entry points and configuration
│   │   └── core/internal/       # Internal core implementation (DatadogCore, CoreFeature, lifecycle)
│   ├── api/                     # API surface definition (for binary compatibility checks)
│   └── build.gradle.kts         # Core module build configuration
│
├── dd-sdk-android-internal/     # Internal utilities (not part of public API)
│   └── src/main/java/          # Java-only utilities for internal use
│
├── features/                    # Feature modules (each implements Feature interface)
│   ├── dd-sdk-android-logs/     # Log collection feature
│   ├── dd-sdk-android-rum/      # Real User Monitoring (views, actions, resources, errors)
│   ├── dd-sdk-android-trace/    # Distributed tracing (OpenTelemetry support)
│   ├── dd-sdk-android-trace-api/    # Public tracing API
│   ├── dd-sdk-android-trace-internal/ # Trace internals
│   ├── dd-sdk-android-trace-otel/    # OpenTelemetry integration
│   ├── dd-sdk-android-ndk/      # Native crash reporting (NDK/C++)
│   ├── dd-sdk-android-profiling/ # Continuous profiling
│   ├── dd-sdk-android-flags/    # Feature flags (DD Feature Management)
│   ├── dd-sdk-android-flags-openfeature/ # OpenFeature protocol support
│   ├── dd-sdk-android-session-replay/   # Screen recording/replay
│   ├── dd-sdk-android-session-replay-compose/ # Compose UI session replay
│   ├── dd-sdk-android-session-replay-material/ # Material Design session replay
│   ├── dd-sdk-android-rum-debug-widget/ # RUM debug floating widget (dev tool)
│   └── dd-sdk-android-webview/  # WebView instrumentation
│
├── integrations/                # Third-party library integrations
│   ├── dd-sdk-android-okhttp/   # OkHttp network interceptor
│   ├── dd-sdk-android-okhttp-otel/ # OkHttp OpenTelemetry support
│   ├── dd-sdk-android-cronet/   # Cronet (Chrome) network stack support
│   ├── dd-sdk-android-coil/     # Coil image loading (v1.0)
│   ├── dd-sdk-android-coil3/    # Coil image loading (v3.0)
│   ├── dd-sdk-android-glide/    # Glide image loading
│   ├── dd-sdk-android-fresco/   # Fresco image loading
│   ├── dd-sdk-android-apollo/   # Apollo GraphQL client
│   ├── dd-sdk-android-sqldelight/ # SQLDelight database
│   ├── dd-sdk-android-timber/   # Timber logging framework
│   ├── dd-sdk-android-rx/       # RxJava support
│   ├── dd-sdk-android-rum-coroutines/ # Kotlin Coroutines for RUM
│   ├── dd-sdk-android-trace-coroutines/ # Kotlin Coroutines for tracing
│   ├── dd-sdk-android-compose/  # Jetpack Compose instrumentation
│   └── dd-sdk-android-tv/       # Android TV specific support
│
├── sample/                      # Example applications
│   ├── kotlin/                  # Kotlin-based sample app
│   ├── automotive/              # Android Automotive sample
│   ├── tv/                      # Android TV sample
│   ├── wear/                    # Wear OS sample
│   └── benchmark/               # Performance benchmarking app
│
├── buildSrc/                    # Gradle build customizations and plugins
│   ├── src/main/kotlin/com/datadog/gradle/ # Build config (AndroidConfig, Dependencies)
│   └── build.gradle.kts
│
├── instrumented/                # Instrumentation tests (integration tests)
│   └── integration/             # Integration test suite
│
├── ci/                         # CI/CD pipeline definitions
│   ├── pipelines/              # GitLab CI or GitHub Actions configs
│   └── scripts/                # Build and deployment scripts
│
├── config/                     # Configuration templates
│   └── *.xml, *.json          # Shared config files
│
├── docs/                       # Documentation and images
│   └── images/                # Screenshot assets
│
├── gradle/                     # Gradle wrapper and settings
│   └── wrapper/
│
├── build.gradle.kts           # Root build configuration
├── settings.gradle.kts        # Gradle module definitions
├── CHANGELOG.md               # Release notes
├── CONTRIBUTING.md            # Development guidelines
├── LICENSE                    # Apache License 2.0
├── README.md                  # Project overview
└── ZEN.md                     # Design principles
```

## Directory Purposes

**dd-sdk-android-core:**
- Purpose: Foundation SDK - context management, persistence, upload coordination, lifecycle
- Contains: `SdkCore` implementation (`DatadogCore`), feature registration, upload scheduler
- Key files: `Datadog.kt` (entry point), `DatadogCore.kt` (main instance), `CoreFeature.kt`, `ContextProvider.kt`

**dd-sdk-android-internal:**
- Purpose: Internal Java utilities (not exposed in public API)
- Contains: Helper classes for internal SDK use only
- Access: Only internal code references; excluded from public artifact

**features/dd-sdk-android-rum:**
- Purpose: Real User Monitoring - collect app views, user interactions, network resources, errors
- Key subdirectories:
  - `internal/monitor/` - `DatadogRumMonitor` (main RUM event handler)
  - `internal/domain/` - Event scope management and domain models
  - `internal/tracking/` - Activity/view tracking, gesture detection
  - `internal/vitals/` - Performance metrics (CPU, memory, FPS)
  - `internal/anr/` - ANR (Application Not Responding) detection
  - `internal/instrumentation/` - UI event instrumentation

**features/dd-sdk-android-logs:**
- Purpose: Log collection - capture app logs and forward to Datadog
- Key subdirectories:
  - `internal/logger/` - `DatadogLogHandler` (main log event handler)
  - `internal/domain/` - Log event generation
  - `internal/storage/` - `LogsDataWriter` for persistence

**features/dd-sdk-android-trace:**
- Purpose: Distributed tracing - APM spans and trace context propagation
- Key subdirectories:
  - `internal/domain/` - Span creation and management
  - `internal/net/` - Request factory for trace intake

**features/dd-sdk-android-session-replay:**
- Purpose: Screen recording - capture view hierarchies and user interaction
- Key subdirectories:
  - `internal/processor/` - View processing pipeline
  - `internal/recorder/` - Screen recording logic

**integrations/dd-sdk-android-okhttp:**
- Purpose: OkHttp network interceptor - automatically track HTTP requests as RUM resources
- Key files: `DatadogEventListener`, `DatadogInterceptor`

**buildSrc:**
- Purpose: Custom Gradle build logic
- Key files: `AndroidConfig.kt` (SDK version, target SDK), `Dependencies.kt` (dependency management)
- Used by: All module build.gradle.kts files

**sample/kotlin:**
- Purpose: Reference implementation showing SDK initialization and usage
- Contains: Android app with Activities, Features usage examples
- Used by: Integration testing, manual QA, developer reference

## Key File Locations

**Entry Points:**
- `dd-sdk-android-core/src/main/kotlin/com/datadog/android/Datadog.kt`: SDK initialization
- `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/configuration/Configuration.kt`: Configuration builder
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/GlobalRumMonitor.kt`: RUM public API
- `features/dd-sdk-android-logs/src/main/kotlin/com/datadog/android/log/Logger.kt`: Logs public API

**Configuration:**
- `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/configuration/Configuration.kt`: Main config
- `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/configuration/Credentials.kt`: Auth config
- `buildSrc/src/main/kotlin/com/datadog/gradle/config/AndroidConfig.kt`: SDK build-time config

**Core Logic:**
- `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/DatadogCore.kt`: Main SDK instance
- `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/CoreFeature.kt`: Core feature implementation
- `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/SdkCoreRegistry.kt`: Feature registry
- `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/RumFeature.kt`: RUM feature
- `features/dd-sdk-android-logs/src/main/kotlin/com/datadog/android/log/internal/LogsFeature.kt`: Logs feature

**Testing:**
- `dd-sdk-android-core/src/test/kotlin/`: Unit tests (co-located)
- `dd-sdk-android-core/src/testFixtures/`: Test fixtures and helpers
- `instrumented/integration/`: Integration tests

**Public APIs:**
- `dd-sdk-android-core/src/main/kotlin/com/datadog/android/api/`: Core interfaces
  - `SdkCore.kt` - Main SDK contract
  - `Feature.kt` - Plugin contract
  - `storage/DataWriter.kt` - Event writing
  - `storage/EventBatchWriter.kt` - Batch storage
  - `net/RequestFactory.kt` - HTTP generation
  - `context/DatadogContext.kt` - Runtime context
- `features/*/api/`: Feature-specific API surfaces

## Naming Conventions

**Files:**
- Feature files: `*Feature.kt` (e.g., `RumFeature.kt`, `LogsFeature.kt`)
- Data writers: `*DataWriter.kt` (e.g., `RumDataWriter.kt`, `LogsDataWriter.kt`)
- Request factories: `*RequestFactory.kt` (e.g., `RumRequestFactory.kt`)
- Monitors/handlers: `*Monitor.kt`, `*Handler.kt` (e.g., `DatadogRumMonitor.kt`)
- Models/events: `*Event.kt`, `*Model.kt` (e.g., `ViewEvent.kt`)
- Public APIs: `Global*.kt` (e.g., `GlobalRumMonitor.kt`)
- Internal: Prefixed with folder name (e.g., `internal/ContextProvider.kt`)
- No-op implementations: `NoOp*.kt` (e.g., `NoOpDataWriter.kt`)

**Directories:**
- Feature internals: `internal/`
- Domain logic: `domain/`
- Network/HTTP: `net/`
- Storage: `storage/` or `persistence/`
- Event models: `model/`
- Utilities: `utils/`
- Tracking strategies: `tracking/`
- Feature-specific: Named with feature (e.g., `vitals/`, `anr/`, `instrumentation/`)

## Where to Add New Code

**New Feature (e.g., new telemetry collector):**
- Create: `features/dd-sdk-android-{feature-name}/`
- Structure:
  - `src/main/kotlin/com/datadog/android/{feature-name}/`
    - `{Feature}Feature.kt` implementing `Feature` interface
    - `internal/` with implementations
    - `{Feature}Monitor.kt` or public API entry point
  - `src/main/kotlin/com/datadog/android/{feature-name}/configuration/` for config
  - `api/` directory for public API surfaces
  - `build.gradle.kts` (copy from existing feature, adjust name)
- Register: Add to `settings.gradle.kts` and feature registry
- Test: Create `src/test/kotlin/` parallel structure

**New Component within Existing Feature (e.g., new RUM tracking strategy):**
- Location: Add to appropriate `features/dd-sdk-android-{feature}/src/main/kotlin/` subdirectory
- If public: Place in main package, document in feature README
- If internal: Place in `internal/` subpackage, implement internal interface
- Example: `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/tracking/{Strategy}.kt`

**New Integration (e.g., new image loading library support):**
- Create: `integrations/dd-sdk-android-{lib-name}/`
- Structure:
  - `src/main/kotlin/com/datadog/android/{lib}/`
    - Factory or interceptor for library instrumentation
    - Adapter connecting library callbacks to SDK
  - `build.gradle.kts` with library dependency
- Pattern: Interceptor-based (OkHttp pattern) or callback-based
- Example: `integrations/dd-sdk-android-glide/src/main/kotlin/com/datadog/android/glide/GlideInterceptor.kt`

**New Utility/Helper:**
- Shared SDK utilities: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/utils/`
- Feature-specific: `features/{feature}/src/main/kotlin/.../utils/`
- Internal only (not exposed): Place in `internal/` subpackage

**Tests for New Code:**
- Co-located: `src/test/kotlin/` mirroring source structure
- Format: `{ClassName}Test.kt` or `{ClassName}Spec.kt`
- Fixtures: `src/testFixtures/` for shared test data/helpers
- Integration: `instrumented/integration/src/` for end-to-end tests

## Special Directories

**buildSrc:**
- Purpose: Gradle build plugin customization
- Generated: No, committed to repo
- Committed: Yes
- Customizations:
  - `AndroidConfig.kt` - SDK version, target API levels, build flavors
  - `Dependencies.kt` - Centralized dependency catalog (versions, repositories)
  - `registerSubModuleAggregationTask()` - Convenience tasks for testing all modules

**gradle/wrapper:**
- Purpose: Gradle distribution version lock
- Generated: No, committed for reproducible builds
- Committed: Yes, includes `gradle-wrapper.jar` and `gradle-wrapper.properties`

**sample/**
- Purpose: Reference implementations
- Generated: No, maintained examples
- Committed: Yes, used for CI/CD validation
- Contents: Runnable Android apps demonstrating SDK usage

**instrumented/integration/**
- Purpose: Integration tests (require Android device/emulator)
- Generated: Test outputs only
- Committed: Test source code only, not build artifacts
- Run: Via `./gradlew instrumentTest` or CI

**.planning/codebase/**
- Purpose: Architecture documentation for GSD pipeline
- Generated: Manually created by mapping process
- Committed: Yes, for pipeline continuity
- Contents: ARCHITECTURE.md, STRUCTURE.md, CONVENTIONS.md, TESTING.md, CONCERNS.md, STACK.md, INTEGRATIONS.md

**api/ (in each module)**
- Purpose: Binary API surface for compatibility checks
- Generated: By Gradle plugin during build (`binary-compatibility-plugin`)
- Committed: Yes, tracked for API evolution monitoring
- Files: `*.api` files defining public class/method signatures

---

*Structure analysis: 2026-01-21*
