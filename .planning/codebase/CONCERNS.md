# Codebase Concerns

**Analysis Date:** 2026-01-21

## Tech Debt

**Thread Safety in File Operations:**
- Issue: `BatchFileOrchestrator` explicitly marked as needing thread-safety improvements. Multiple mutable state variables (`previousFile`, `previousFileItemCount`, `lastFileAccessTimestamp`, `lastCleanupTimestamp`) without synchronization.
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/persistence/file/batch/BatchFileOrchestrator.kt` (line 29)
- Impact: Potential race conditions when multiple threads access file operations concurrently, leading to data corruption or inconsistent state
- Fix approach: Implement proper synchronization using locks or atomic operations for state tracking, implement caching mechanism for file lookups to reduce syscalls

**Performance Metrics Collection Gap:**
- Issue: Event processing and event write measurement performance metrics not yet implemented
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/persistence/ConsentAwareStorage.kt` (line 58)
- Impact: Lack of visibility into SDK's own performance overhead, making bottleneck identification difficult
- Fix approach: Add instrumentation to measure event processing time and file write duration

**NDK Data Encryption:**
- Issue: NDK crash data is not encrypted despite other SDK data being encrypted by default
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/ndk/internal/DatadogNdkCrashHandler.kt` (line 67)
- Impact: Sensitive crash data stored in plaintext, potential security risk
- Fix approach: Apply same encryption mechanism used for other event types to NDK crash data

**Large Class Complexity:**
- Issue: Multiple core classes exceed recommended size limits with significant responsibilities
- Files:
  - `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/CoreFeature.kt` (827 lines)
  - `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/DatadogCore.kt` (752 lines)
  - `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/scope/RumViewScope.kt` (1732 lines)
- Impact: Difficult to maintain, test, and modify; high cognitive complexity; increased likelihood of bugs
- Fix approach: Break into smaller, focused classes with single responsibilities; extract helper classes for specific concerns

**Compose Threading Rules Not Validated:**
- Issue: Explicit suppression of thread safety checks in Compose integration with TODO to validate composable threading rules
- Files:
  - `integrations/dd-sdk-android-compose/src/main/kotlin/com/datadog/android/compose/Navigation.kt` (lines 118, 122)
  - `integrations/dd-sdk-android-compose/src/main/kotlin/com/datadog/android/compose/Navigation3.kt` (lines 176, 179)
- Impact: Potential thread safety violations in Compose code that could cause crashes or inconsistent state
- Fix approach: Review and validate Compose thread safety requirements, document constraints or implement proper synchronization

**Deprecated Android API Usage:**
- Issue: `Bundle#get` is deprecated with no direct replacement, but still used for tracking fragment arguments
- Files: `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/tracking/BundleExt.kt` (line 22)
- Impact: Future Android API breaks may invalidate tracking functionality
- Fix approach: Investigate alternative Bundle access mechanisms or implement compatibility layer

## Known Bugs

**Flaky Tests (Historically Common):**
- Symptoms: Test failures that are intermittent and environment-dependent
- Files: Multiple test files throughout codebase
- Trigger: Timing-dependent operations, particularly in async/concurrent tests
- Workaround: Tests have been individually fixed, but pattern persists in new code
- Priority: Address in test infrastructure to prevent recurrence

**Session Replay Flaky Mapper Test:**
- Symptoms: ViewWireframeMapperTest intermittently fails
- Files: `features/dd-sdk-android-session-replay/src/test/kotlin/com/datadog/android/sessionreplay/internal/recorder/mapper/ViewWireframeMapperTest.kt` (line 60)
- Trigger: Timing-dependent wireframe mapping operations
- Workaround: Marked with TODO, test needs deterministic timing
- Priority: Medium

**StackOverflowError Potential:**
- Symptoms: Application crash with stack overflow
- Files: Addressed in PR #2990
- Trigger: Likely in recursive operations or deeply nested data structures
- Status: Fixed in v3.4.0

## Security Considerations

**Sensitive Data in Logs:**
- Risk: Client tokens, API keys, or authentication data might be logged inadvertently
- Files: Various logging throughout SDK
- Current mitigation: Code review practices, regex patterns in trace/trace-internal for redacting credentials
- Recommendations:
  - Implement structured logging filters to automatically redact known sensitive patterns
  - Add linting rules to detect patterns like "token=", "key=", "password="
  - Include security training for contributors

**Token Validation:**
- Risk: Invalid client tokens accepted without immediate feedback
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/data/upload/UploadStatus.kt` (lines 109, 134)
- Current mitigation: Errors tracked in upload responses, logged to user
- Recommendations: Add proactive token validation on SDK initialization

**Encryption Key Management:**
- Risk: Custom encryption implementations may not be secure
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/security/Encryption.kt`
- Current mitigation: Public interface allows app to implement custom encryption
- Recommendations:
  - Document encryption best practices for implementers
  - Consider providing reference implementation using Android EncryptedSharedPreferences pattern
  - Add integration tests for encryption/decryption round-trip

## Performance Bottlenecks

**File System Operations:**
- Problem: Frequent syscalls for checking file existence and sizes in batch operations
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/persistence/file/batch/BatchFileOrchestrator.kt`
- Cause: Each batch write/read involves multiple file operations without caching
- Impact: High disk I/O under heavy event load, potential ANR on slow storage
- Improvement path: Implement file cache with TTL and batch syscalls; use `File.walk()` for directory traversal instead of repeated queries

**Span ID Generation:**
- Problem: No caching for span ID generation despite documented TODO with external reference
- Files: `features/dd-sdk-android-trace-internal/src/main/java/com/datadog/trace/api/DDSpanId.java` (lines 77, 88, 99)
- Cause: Multiple identical conversion operations on same span IDs
- Impact: CPU overhead in high-throughput tracing scenarios
- Improvement path: Implement LRU cache for DDSpanId conversions

**Image Wireframe Processing:**
- Problem: ImageWireframeHelper function has excessive parameters and multiple responsibilities
- Files: `features/dd-sdk-android-session-replay/src/main/kotlin/com/datadog/android/sessionreplay/utils/ImageWireframeHelper.kt` (line 110)
- Cause: Complex image processing logic consolidated in one function
- Impact: Increased CPU/memory during session replay recording with heavy image usage
- Improvement path: Decompose into smaller functions, implement lazy processing

**View Bounds Resolution:**
- Problem: Creates object allocation for every bounds query instead of returning primitive array
- Files: `features/dd-sdk-android-session-replay/src/main/kotlin/com/datadog/android/sessionreplay/utils/ViewBoundsResolver.kt` (line 25)
- Cause: API design choice for type safety
- Impact: Memory pressure during session replay recording
- Improvement path: Use primitive array or coordinate class with @JvmInline value class

## Fragile Areas

**RUM View Scope:**
- Files: `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/domain/scope/RumViewScope.kt`
- Why fragile:
  - 1732 lines with TooManyFunctions and LargeClass suppressions
  - Manages multiple concerns: lifecycle, metrics, events, listeners
  - Complex state tracking for view start/end, actions, resources
  - 80+ parameters in constructor chain
- Safe modification:
  - Use feature flags for new functionality
  - Add comprehensive tests for state transitions
  - Consider extracting metric handling into separate strategy class
- Test coverage: High test coverage exists but edge cases in state transitions may not be fully covered

**Drawable to Color Mapping:**
- Files: `features/dd-sdk-android-session-replay/src/main/kotlin/com/datadog/android/sessionreplay/internal/recorder/mapper/AndroidQDrawableToColorMapper.kt`
- Why fragile:
  - Handles complex drawable type conversions
  - Not all blend modes resolved (TODO RUM-3469)
  - Potential for color space mismatches
- Safe modification: Add specific drawable type handling in isolation, test color output
- Test coverage: Blend mode handling not fully tested

**Feature Context Management:**
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/ContextProvider.kt`
- Why fragile:
  - Lifecycle checks may be needed but not fully implemented (TODO)
  - Context nullability unclear
  - Used across multiple features without clear contract
- Safe modification: Document contract clearly, add integration tests
- Test coverage: Unit tests exist but lifecycle edge cases not covered

**Batch File Orchestration:**
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/persistence/file/batch/BatchFileOrchestrator.kt`
- Why fragile:
  - 370 lines, TooManyFunctions suppression
  - Marked as needing thread-safety improvements
  - Manual memory management of mutable state
- Safe modification: Use read-write locks, document assumptions about call patterns
- Test coverage: Tests exist but concurrent access scenarios need improvement

## Scaling Limits

**Core Feature Initialization:**
- Current capacity: Single SDKCore instance primarily tested
- Limit: Multiple SDK instances (multiple apps, multi-process) now supported but edge cases exist
- Scaling path:
  - Test and document multi-instance limits per Android version
  - Consider resource pooling for shared services
  - Monitor per-instance memory footprint

**File Storage Throughput:**
- Current capacity: Depends on device storage speed and SDK batch configuration
- Limit: Under sustained high-event volume, file I/O may become bottleneck
- Scaling path:
  - Implement batch size auto-adjustment based on device capabilities
  - Add metrics for file operation duration
  - Consider in-memory ring buffer for temporary buffering

**RUM Event Processing:**
- Current capacity: Event processing delegates to worker thread pool
- Limit: Executor service backpressure may drop events under extreme load
- Scaling path: Make backpressure strategy configurable, add metrics for queue depth

## Dependencies at Risk

**Kronos Time Provider:**
- Risk: External NTP library dependency (Kronos) for time synchronization
- Impact: If library becomes unmaintained or has bugs, clock synchronization fails
- Evidence: Bug fixed in v3.4.0 (KronosTimeProvider crash)
- Migration plan:
  - Consider switching to Android System.currentTimeMillis() for fallback
  - Evaluate alternative NTP libraries (e.g., ntpd-based approach)
  - Document clock sync failure behavior

**Gson Serialization:**
- Risk: Gson used throughout for JSON serialization, JSON parsing edge cases
- Impact: Malformed events if Gson version has bugs or unsupported types
- Migration plan: Consider kotlinx.serialization or kotlinx.json for future versions

**OkHttp Client:**
- Risk: Network stack depends on OkHttp version, potential security issues
- Impact: Vulnerabilities in OkHttp propagate to SDK
- Current mitigation: Regular dependency updates tracked in CHANGELOG
- Recommendations: Pin to specific patch version, subscribe to security advisories

**dd-trace-java Dependency:**
- Risk: Vendored trace-java code may diverge from upstream
- Impact: Trace events may become incompatible with backend expectations
- Files: `features/dd-sdk-android-trace-internal/` (vendored from dd-trace-java)
- Recommendations: Document versioning alignment, plan for periodic sync

## Missing Critical Features

**Session Replay Checkbox Display:**
- Problem: Masked checkboxes use outdated unchecked shape wireframe
- Blocks: Accurate session replay for forms with masked input fields
- Files: `features/dd-sdk-android-session-replay/src/main/kotlin/com/datadog/android/sessionreplay/internal/recorder/mapper/CheckableTextViewMapper.kt` (line 68)
- Priority: Medium (privacy feature trade-off for usability)

**Passthrough Mechanism for JVM Crash:**
- Problem: Better passthrough mechanism for JVM crash scenario not yet implemented
- Blocks: Full context propagation for JVM crashes in some scenarios
- Files:
  - `dd-sdk-android-core/src/main/kotlin/com/datadog/android/api/feature/FeatureScope.kt` (line 55)
  - `features/dd-sdk-android-rum/src/main/kotlin/com/datadog/android/rum/internal/monitor/DatadogRumMonitor.kt` (line 788)
- Priority: Low-Medium

**Multiple Spans in Single Payload:**
- Problem: Trace serializer cannot handle multiple spans in one payload
- Blocks: More efficient trace event batching
- Files: `features/dd-sdk-android-trace/src/main/kotlin/com/datadog/android/trace/internal/data/CoreTraceWriter.kt` (line 50)
- Priority: Low (workaround exists, batching happens at higher level)

## Test Coverage Gaps

**Compose Navigation Threading:**
- What's not tested: Thread safety of composable state mutations during navigation
- Files: `integrations/dd-sdk-android-compose/src/main/kotlin/com/datadog/android/compose/Navigation.kt`
- Risk: Race condition crashes when navigation occurs off main thread
- Priority: High (threading violations are hard to debug)

**NDK Data Flow:**
- What's not tested: Full NDK crash detection and RUM event generation pipeline
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/ndk/internal/DatadogNdkCrashHandler.kt`
- Risk: NDK crashes may not be properly reported or may corrupt event stream
- Priority: High (critical error path)

**GraphQL Header Handling:**
- What's not tested: Non-ASCII GraphQL headers in various character encodings
- Files: Internal network handling code
- Risk: Character encoding issues with international apps
- Priority: Medium

**Encryption Round-Trip:**
- What's not tested: Encryption/decryption of events with various data types
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/security/Encryption.kt`
- Risk: Data corruption or loss if encryption/decryption fails silently
- Priority: Medium

**File System Concurrency:**
- What's not tested: Concurrent batch file operations under high load
- Files: `dd-sdk-android-core/src/main/kotlin/com/datadog/android/core/internal/persistence/file/batch/BatchFileOrchestrator.kt`
- Risk: Race conditions causing file corruption or lost events
- Priority: High

---

*Concerns audit: 2026-01-21*
