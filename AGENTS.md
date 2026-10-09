# Module Map

- `dd-sdk-android-core` — SDK core: `Datadog`, `InternalLogger`, storage/upload, shared executors (`CoreFeature`).
- `dd-sdk-android-internal` — internals shared by several modules (only put code here if more than one module needs it).
- `features/*` — product features (RUM, Logs, Trace, Session Replay, Profiling, Flags, NDK, WebView, …).
- `integrations/*` — integrations with third-party libraries (OkHttp, Cronet, Compose, Coil, Glide, Apollo, …).
- `reliability/` — integration tests; `reliability/single-fit/*` holds per-feature E2E tests.
- `instrumented/integration` — legacy integration test suite.
- `tools/*` — custom detekt and lint rules, `noopfactory` code generator, shared unit test utilities (`tools/unit`).
- `sample/*` — sample apps (`sample/kotlin` is the main one).

# Build Commands

The root `build.gradle.kts` registers aggregate tasks that run across all submodules:

```bash
./gradlew assembleLibrariesDebug     # build all library modules (debug)
./gradlew assembleLibraries          # build all library modules (debug + release)
./gradlew unitTestDebug              # unit tests, all modules, debug variant
./gradlew unitTestRelease            # unit tests, all modules, release variant
./gradlew lintCheckAll               # lint all modules
./gradlew checkAll                   # lint + unit tests + instrumented tests — requires connected device/emulator
./gradlew instrumentTestAll          # all instrumented (integration) tests — requires connected device/emulator
```

`checkAll` includes `instrumentTestAll`, so it fails or hangs without a device. Without a device, run `./gradlew lintCheckAll unitTestAll` instead (`unitTestAll` covers debug, release and tool tests).

Prefer per-module tasks, which are much faster than the aggregate ones:

```bash
./gradlew :features:dd-sdk-android-rum:testDebugUnitTest
./gradlew :features:dd-sdk-android-rum:testDebugUnitTest --tests "*RumViewScopeTest*"   # single test class
./gradlew :features:dd-sdk-android-rum:testDebugUnitTest --tests "*RumViewScopeTest.M send event*"   # single test method
```

Sample app requires a flavor name:
```bash
./gradlew :sample:kotlin:assembleUs1Debug   # us1 is the default flavor
```

Code formatting:
```bash
./gradlew <module_name>:ktlintFormat

# or for all modules, with included build
./gradlew ktlintFormatAll
```

Detekt checks:
```bash
./gradlew <module_name>:detekt

# or for all modules
./gradlew detekt
```

The full local CI pipeline is `./local_ci.sh` (see `./local_ci.sh --help` for options, e.g. `--analysis`, `--compile`, `--test`).

# Before You Finish

Run these for every module you changed before considering the work done:

```bash
./gradlew :<module>:ktlintFormat
./gradlew :<module>:detekt              # only modules applying `detekt-conventions` (not tools/*, sample/*, reliability/*, instrumented/*)
./gradlew :<module>:testDebugUnitTest   # Android modules; JVM modules (tools/detekt, tools/lint, tools/noopfactory) use :<module>:test, build-logic uses :build-logic:test
./gradlew checkGeneratedFiles   # if public API, dependencies or Kotlin/AAR metadata may have changed
```

# Checked-in Generated Files

Every publishable module has generated files checked into git. **CI fails if they are stale** (`./gradlew checkGeneratedFiles` verifies all of them). Regenerate with the per-module task:

| File | Update task |
|---|---|
| `api/apiSurface` | `./gradlew :<module>:generateApiSurface` |
| `api/<module>.api` | `./gradlew :<module>:apiDump` |
| `api/compiler-meta.txt` | `./gradlew :<module>:generateCompilerMetadata` |
| `api/aar-metadata.txt` | `./gradlew :<module>:generateAarMetadataInfo` |
| `transitiveDependencies` | `./gradlew :<module>:generateTransitiveDependenciesList` |

`LICENSE-3rdparty.csv` at the repo root is verified by `checkDependencyLicensesAll`. Add an entry for each new third-party dependency, keeping the file in alphabetical order.

# Generated Models

Some modules generate Kotlin data classes from JSON schemas at build time (from `src/main/json/`). The generated Kotlin files land in `build/generated/<generationTaskName>/`. **Do not edit them directly.**

Some schema folders are cloned from the upstream [`rum-events-format`](https://github.com/DataDog/rum-events-format) repo and are overwritten on sync:

- `features/dd-sdk-android-rum/src/main/json/rum` and `json/telemetry` (`cloneRumSchema`, `cloneTelemetrySchema`)
- `features/dd-sdk-android-profiling/src/main/json/profiling`
- `features/dd-sdk-android-session-replay/src/main/json/schemas`

Changes to these must be made upstream in `rum-events-format` first, then synced. The other schema folders (e.g. `dd-sdk-android-core/src/main/json/rc`, `features/dd-sdk-android-logs/src/main/json/log`, `features/dd-sdk-android-trace/src/main/json/trace`, `features/dd-sdk-android-flags/src/main/json/flags`) are local and can be edited directly. Rebuild after editing.

# Code Conventions

These are rules that reviewers repeatedly enforce. Follow them when writing code to avoid review round-trips, and flag violations of them when reviewing a PR.

## Public API

- Keep everything `internal` unless it is meant for customers. Check the `api/apiSurface` diff for anything leaked by accident.
- Don't expose `Impl` classes, internal types in public signatures, or internal classes in public KDoc. Cross-module internal access goes through `_RumInternalProxy`-style proxies or a cast inside the function body.
- If a class is meant to be built via `Builder`, make its constructor `internal` or `private`.
- Return empty collections instead of nullable collections. Prefer a NoOp implementation over a nullable type. Avoid unneeded default arguments.
- Changing a public signature, nullability or a data class shape is a breaking change. Flag it explicitly and prefer deprecate-and-alias. Also consider cross-platform consumers such as Flutter, React Native and KMP.
- Avoid `data class` in public API unless needed (`copy` becomes part of the ABI).
- New public APIs should be consistent with the iOS SDK. If you can't verify iOS parity, say so in the PR description as an open question.

## Threading

- Don't create new threads or executors, if possible. Prefer to reuse existing ones such as `contextExecutorService` or `persistenceExecutorService` in `CoreFeature`. Use `submitSafe`/`executeSafe` from `ConcurrencyExt.kt`.
- Don't block the main thread or SDK initialization. `getFeatureContext` takes a lock. On hot paths, subscribe with `FeatureContextUpdateReceiver` instead.
- Don't stack concurrency primitives, e.g. an `AtomicBoolean` or `ConcurrentHashMap` inside `synchronized`. Prefer immutable data plus a single atomic swap (`getAndSet`) to multiple layers of locking.
- Multiple SDK core instances can coexist. Don't keep mutable state in `object`s or companion objects; scope it to the `SdkCore` instance, so that it doesn't leak between instances or get delivered to only one of them. If a feature holds shared state, add a test with two cores.

## Logging and Errors

- Log only via `InternalLogger`, obtained from the relevant SDK core. Never use `android.util.Log`, `println` or `System.err` in SDK code. The sample app uses Timber.
- Choose the target deliberately, following the rule of thumb in the `InternalLogger` KDoc. `USER` is for messages that are actionable or report the main data-processing steps (tracking, storage, upload). `TELEMETRY` is for events we need to monitor. `MAINTAINER` is for internal details and is visible only in SDK debug builds. Use `onlyOnce = true` for messages that could spam: log and telemetry spam costs customers money and hides real problems.
- Pass the exception as the `throwable` argument. Don't put `e.message` in the log text, since the logger already prints it.
- Never throw from SDK code paths that could reach the host app. Catch narrow exception types (e.g. `IOException`), not `Throwable` or `Exception`. Exception to this rule: calls into customer-provided or third-party code (event mappers, serializers, listeners) whose exceptions are unknown: there, catching `Throwable` is intentional (see `Serializer.serializeToByteArray`); suppress `TooGenericExceptionCaught` at that site and log the exception. Don't swallow exceptions silently. Don't create a bare `Throwable` or `Exception`; use a specific subclass.

## Static Analysis and Compatibility

- `MIN_SDK` is 23. Guard newer APIs with `BuildSdkVersionProvider` (it is annotated with `@ChecksSdkIntAtLeast`). Don't use `@SuppressLint("NewApi")`: it hides crashes on older API levels, while `BuildSdkVersionProvider` can be mocked in tests to cover both branches.
- Android and third-party calls that can throw must be declared in `detekt_custom_unsafe_calls.yml` and handled. Only genuinely non-throwing calls go into the `detekt_custom_safe_calls_*.yml` files. Keep these files sorted. The `UnsafeThirdPartyFunctionCall` rule skips the SDK's own `com.datadog.*` calls, so handle exceptions thrown by SDK code explicitly; listing them in these files has no effect.
- Place `@Suppress(...)` on the narrowest scope: the call site, not the whole method or class.
- Use `Locale.US` for case conversions and formatting. The default locale gives wrong results in some locales (e.g. Turkish dotless `i`).
- Dependencies go in `gradle/libs.versions.toml`. Prefer `implementation` over `api`. A new module must be registered in `settings.gradle.kts`. To make it publishable, apply the `maven-publish` and `signing` plugins and call `applyPublishingConfig(...)` in its `datadogBuild { }` block (see `integrations/dd-sdk-android-cronet/build.gradle.kts`). CI publishing (`publishToSonatype`) and `local_ci.sh` pick it up automatically.
- Prefer JSON schemas and generated models over hand-written JSON parsing (see [Generated Models](#generated-models)).
- Don't put code into `dd-sdk-android-internal` if only one module uses it.

## Code Style

- Use imports instead of fully qualified names.
- Order class members as: properties, overridden/interface methods, then private methods. Put `companion object` and nested `Builder` at the bottom. Mark the companion `internal` or `private` when possible.
- Don't add comments or KDoc that restate the code. Use `@inheritDoc` instead of copy-pasting KDoc. Keep existing comments accurate when changing code.
- Names must be precise. For example, use `set` rather than `add` for a single value. Don't use names that clash with existing concepts, such as "elapsed" vs `elapsedRealtime`. Don't put `Thread` in the name of something that isn't a thread.
- Prefer the type system: sealed classes or enums over strings, `T : Any` over `T?`, Kotlin function types over `java.util.function.*`.

# Testing

## Unit Tests

Test class boilerplate (JUnit5 + Mockito + Elmyr):

```kotlin
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class FooTest {
    lateinit var testedFoo: Foo       // object under test: prefix "tested"
    @Mock lateinit var mockBar: Bar   // mock being verified: prefix "mock"
    @Mock lateinit var stubBaz: Baz   // mock with preset behavior: prefix "stub"
    // fake data/fixtures: prefix "fake"

    @Test
    fun `M do something W foo() {some context}`(
        @StringForgery fakeKey: String,
        @Forgery fakeConfig: SomeConfig
    ) {
        // Given
        // When
        // Then
    }
}
```

Test method naming: `` `M <expected behavior> W <method()> {context}` ``

Test rules:

- Always split the body with `// Given`, `// When`, `// Then` comments.
- Use forged values (`@StringForgery`, `@Forgery`, `forge.anXxx()`) for incidental data whose exact value doesn't matter. Randomize values that could clash with defaults. Keep fixed literals when the value defines the tested behavior (boundary values such as a `0f` sample rate, protocol constants).
- Use `checkNotNull(x)` for smart casts instead of `!!` or `?.`.
- Use `argumentCaptor` instead of `argThat`, since `argThat` gives a generic failure message. Don't use `any()` when the argument can be asserted. Don't add a captor whose value is never read.
- Omit `eq()` when all matchers would be `eq`. Omit `times(1)`, which is the default. Prefer `never()` instead of `times(0)`.
- When verifying a log call, assert the message too, not only level and target.
- Don't use `Thread.sleep` or `delay`, and avoid real threads in tests, because they make tests flaky. Use a same-thread executor or a fake time provider. Exception: tests whose purpose is concurrency (races, deadlocks) may use real threads, synchronized with latches and bounded waits (`await(timeout)`), never `sleep`.
- Make sure the test fails if the behavior breaks: after writing it, briefly break the code under test (or invert the assertion) and check that the test goes red.
- Don't test data class getters or language features. Remove tests already covered by another test. Merge near-duplicate tests with `@ParameterizedTest` or `@EnumSource`.
- Don't use reflection or static mocks for code we own: they break silently on refactors. Change visibility to `internal` instead.
- Use `assert*` helpers (AssertJ `assertThat`), never the Kotlin `assert` keyword, which is a no-op without a JVM flag.
- E2E-style tests belong in `reliability/single-fit`, not in feature module unit tests.

### Elmyr Forge

Each module has a forge configurator (usually `Configurator` or `ForgeConfigurator`, typically under `src/test/.../forge/`) extending `BaseConfigurator` (from `tools/unit`). It registers all `ForgeryFactory` implementations for that module's types. Find the one referenced by `@ForgeConfiguration(...)` in the module's existing tests; don't create a new one. When adding a new data class used in tests, add a `ForgeryFactory` and register it in that configurator.

Shared factories: `forge.useCoreFactories()` (from `dd-sdk-android-core` testFixtures).

## Integration Tests

`instrumented/integration`, `reliability/core-it` and `reliability/with-backend` contain instrumented tests that run on a connected device or emulator.

`reliability/single-fit/*` contains E2E tests that run on the JVM without a device, e.g. `./gradlew :reliability:single-fit:rum:testReleaseUnitTest` (this is what CI runs).

```bash
./gradlew instrumentTestAll                                        # all integration tests
./gradlew :instrumented:integration:connectedDebugAndroidTest     # legacy integration suite
./gradlew :reliability:core-it:connectedDebugAndroidTest          # core integration tests
```

# Sample App Config

The sample app reads credentials from gitignored JSON files in `config/`. Missing files don't break the build (empty strings are used), but the app won't send data to Datadog. Schema (from `build-logic/.../SampleAppConfig.kt`):

```json
{
  "token": "",
  "rumApplicationId": "",
  "apiKey": "",
  "applicationKey": "",
  "logsEndpoint": "",
  "tracesEndpoint": "",
  "rumEndpoint": "",
  "sessionReplayEndpoint": "",
  "remoteConfigurationId": ""
}
```

Filename matches the flavor: `config/us1.json`, `config/staging.json`, etc. Get credentials from your Datadog org.

To inspect the events the SDK emits from the sample app, use the `android-sdk-event-inspection` skill (`.claude/skills/android-sdk-event-inspection`).

# Branches and Commits

- Default branch for PRs: **`develop`**
- Branch name format: `<username>/<ticket>/<short-description>`, e.g. `nogorodnikov/rum-18858/replace-argthat-with-captor`.
- Commit title format: `<JIRA-KEY>-<number>: <short description>`, e.g. `RUM-18858: Replace argThat matchers with argument captors`. The Jira project varies (`RUM`, `RUMS`, `PANA`, `FFL`, `FFLSDK`, …).
- Each commit must reference the ticket number in the title. If you don't know the ticket, ask for it; never invent one.

# Pull Requests

- Use the project PR template (`.github/PULL_REQUEST_TEMPLATE.md`).
- Keep PRs focused. Don't include unrelated refactors, formatting churn, debug leftovers, stray files or "may be needed later" code (unused properties, schemas, files).
- Don't edit `CHANGELOG.md` in feature PRs. It is updated only in release preparation PRs.
- When preparing a release changelog: tags reflect customer impact, not change size. Internal-only changes are `[MAINTENANCE]`, not `[IMPROVEMENT]`. Follow the existing entry format: `* [TAG] Description. See [#N](link)`.

# Dogfooding Branch

`dogfooding` is managed only through the scripts in `ci/scripts/dogfooding/`; read `ci/scripts/dogfooding/README.md` before helping with it. Never push to `dogfooding`, open a PR from a feature branch into it, or merge into it with squash or rebase: use `feature.sh`, `sync.sh` and `reset.sh`, which open the PRs. The only change made by hand is reverting a feature's dogfood merges, as described in the README's "Remove a single feature". Show the command and ask before running the scripts, since they push branches and open PRs.
