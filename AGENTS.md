# Build Commands

The root `build.gradle.kts` registers aggregate tasks that run across all submodules:

```bash
./gradlew assembleLibrariesDebug     # build all library modules (debug)
./gradlew assembleLibraries          # build all library modules (debug + release)
./gradlew unitTestDebug              # unit tests, all modules, debug variant
./gradlew unitTestRelease            # unit tests, all modules, release variant
./gradlew lintCheckAll               # lint all modules
./gradlew checkAll                   # lint + unit tests + instrumented tests
./gradlew instrumentTestAll          # all instrumented (integration) tests — requires connected device/emulator
```


Sample app requires a flavor name:
```bash
./gradlew :sample:kotlin:assembleUs1Debug   # us1 is the default flavor
```

Code formatting (requires `ktlint` installed):
```bash
ktlint -F "**/*.kt" "**/*.kts" '!**/build/generated/**' '!**/build/kspCaches/**'
```

# API Surface Files

Every publishable module has `api/apiSurface` and `api/<module>.api` checked into git. **CI fails if these are stale.** After any public API change, update them:

```bash
./gradlew :features:dd-sdk-android-rum:generateApiSurface  # updates api/apiSurface
./gradlew :features:dd-sdk-android-rum:apiDump             # updates api/<module>.api
```

Or for all modules: `./gradlew checkApiSurfaceChangesAll` (verifies) vs. running the update tasks per-module.

# Integration Tests

Integration tests live under `instrumented/integration` and `reliability/` and run on a connected device or emulator.

```bash
./gradlew instrumentTestAll                                        # all integration tests
./gradlew :instrumented:integration:connectedDebugAndroidTest     # legacy integration suite
./gradlew :reliability:core-it:connectedDebugAndroidTest          # core integration tests
```

Individual reliability modules (each requires a connected device):
```bash
./gradlew :reliability:single-fit:logs:connectedDebugAndroidTest
./gradlew :reliability:single-fit:rum:connectedDebugAndroidTest
./gradlew :reliability:single-fit:trace:connectedDebugAndroidTest
./gradlew :reliability:single-fit:okhttp:connectedDebugAndroidTest
```

# Testing Conventions

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

## Elmyr Forge

Each module has a `Configurator` in `src/test/.../utils/forge/` extending `BaseConfigurator` (from `tools/unit`). It registers all `ForgeryFactory` implementations for that module's types. When adding a new data class used in tests, add a `ForgeryFactory` and register it in `Configurator`.

Shared factories: `forge.useCoreFactories()` (from `dd-sdk-android-core` testFixtures).

# Sample App Config

The sample app reads credentials from gitignored JSON files in `config/`. Missing files don't break the build (empty strings are used), but the app won't send data to Datadog. Schema (from `buildSrc/.../SampleAppConfig.kt`):

```json
{
  "token": "",
  "rumApplicationId": "",
  "apiKey": "",
  "applicationKey": "",
  "logsEndpoint": "",
  "tracesEndpoint": "",
  "rumEndpoint": "",
  "sessionReplayEndpoint": ""
}
```

Filename matches the flavor: `config/us1.json`, `config/staging.json`, etc. Get credentials from your Datadog org.

# Generated Models

Some modules generate Kotlin data classes from JSON schemas at build time (e.g. `features/dd-sdk-android-rum/src/main/json/`). The generated Kotlin files land in `build/generated/json2kotlin/` — **do not edit them directly**. Edit the JSON schemas instead and rebuild.

# Commits

- Default branch for PRs: **`develop`**
- All commits must be **GPG-signed** (repo policy).
- Commit title format: `RUM-XXXXX: <short description>` (ticket number prefix required)
- Each commit must reference the ticket number in the title.

# Pull Requests

- Use the project PR template (`.github/PULL_REQUEST_TEMPLATE.md`). 
