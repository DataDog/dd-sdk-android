# Testing Patterns

**Analysis Date:** 2026-01-21

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) 5.9.3
- Config: Configured via Gradle in `dd-sdk-android-core/build.gradle.kts`
- Extensions: Mockito, Forge (data generation), custom TestConfiguration extensions

**Assertion Library:**
- AssertJ 3.18.1 (`org.assertj:assertj-core`)
- Used for all assertions: `assertThat(...).isEqualTo(...)`, `assertThat(...).isEmpty()`

**Run Commands:**
```bash
./gradlew :dd-sdk-android-core:testDebugUnitTest              # Run unit tests (debug)
./gradlew :dd-sdk-android-core:testReleaseUnitTest            # Run unit tests (release)
./gradlew unitTestDebug                                         # Run all debug unit tests
./gradlew unitTestAll                                           # Run all unit and integration tests
./gradlew koverReportAll                                        # Generate coverage reports
./gradlew :instrumented:integration:connectedCheck             # Run instrumented/Android tests
```

## Test File Organization

**Location:**
- Co-located with source: `src/test/kotlin/` for unit tests parallel to `src/main/kotlin/`
- Package structure mirrors production: Test in `com.datadog.android.core.configuration` package lives in `src/test/kotlin/com/datadog/android/core/configuration/`
- Instrumented tests: `src/androidTest/kotlin/` (for Android-specific testing)
- Test fixtures: `testFixtures` source set for shared test utilities and builders

**Naming:**
- `<ClassName>Test.kt` for unit tests: `ConfigurationBuilderTest.kt`, `HostsSanitizerTest.kt`
- `<ClassName>AndroidTest.kt` for instrumented tests
- Test class name matches primary class under test

**Structure:**
```
dd-sdk-android-core/
├── src/
│   ├── main/kotlin/com/datadog/android/core/configuration/
│   │   └── Configuration.kt
│   ├── test/kotlin/com/datadog/android/core/configuration/
│   │   └── ConfigurationBuilderTest.kt
│   └── testFixtures/kotlin/com/datadog/android/
│       └── utils/config/*.kt
```

## Test Structure

**Suite Organization:**
```kotlin
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = Configurator::class)
internal class ConfigurationBuilderTest {

    lateinit var testedBuilder: Configuration.Builder

    @BeforeEach
    fun `set up`(forge: Forge) {
        testedBuilder = Configuration.Builder(
            clientToken = forge.anHexadecimalString(),
            env = forge.aStringMatching("[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]"),
            variant = forge.anElementFrom(forge.anAlphabeticalString(), ""),
            service = forge.aStringMatching("[a-z]+(\\.[a-z]+)+")
        )
    }

    @Test
    fun `M use sensible defaults W build()`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.coreConfig.needsClearTextHttp).isFalse()
    }
}
```

**Patterns:**
- Setup: `@BeforeEach` labeled method `set up()` uses backtick naming
- Teardown: `@AfterEach` if needed for cleanup
- Assertion: Three-phase pattern: Given/When/Then or When/Then
- Comments mark phase: `// Given`, `// When`, `// Then`

## Mocking

**Framework:** Mockito Kotlin 5.1.0 via `org.mockito.kotlin` package

**Patterns:**
```kotlin
// Declaration with @Mock annotation
@Mock
lateinit var mockInternalLogger: InternalLogger

// Creating mocks inline
val mockSanitizer: HostsSanitizer = mock()

// Stubbing behavior
whenever(mockInternalLogger.log(...)).doReturn(Unit)

// Verifying calls
verify(mockInternalLogger, times(hosts.size)).log(
    eq(InternalLogger.Level.ERROR),
    eq(InternalLogger.Target.USER),
    capture(),
    isNull(),
    eq(false),
    eq(null)
)

// Argument captor for assertions
argumentCaptor<() -> String> {
    verify(logger.mockInternalLogger, times(hosts.size)).log(...)
    assertThat(allValues.map { it() })
        .containsExactlyInAnyOrderElementsOf(expectedMessages)
}

// Verifying no interactions
verifyNoInteractions(mockInternalLogger)

// Reset mocks between tests if needed
reset(mockInternalLogger)
```

**What to Mock:**
- External dependencies: `InternalLogger`, `ConnectivityManager`, `NetworkCallback`
- System services: Android system services obtained via `Context.getSystemService()`
- I/O operations: File writers, network calls, database operations
- Time-dependent operations: Use `TimeProvider` abstraction for testing

**What NOT to Mock:**
- Domain objects being tested: Construct real instances of `Configuration`, `HostsSanitizer`
- Value objects and data classes: Create real instances, not mocks
- String utilities and small helper functions: Call directly
- Collections and standard library types: Use real instances

## Fixtures and Factories

**Test Data:**
```kotlin
// Forge for randomized test data
@StringForgery(StringForgeryType.ALPHABETICAL, size = 1)
lateinit var separator: String

@StringForgery(StringForgeryType.NUMERICAL)
lateinit var rawString: String

@IntForgery
var count: Int

@BoolForgery
var enabled: Boolean

@StringForgery(
    regex = "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
        "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
)
lateinit var ipAddress: List<String>

// Using Forge programmatically in test method
fun testSomething(forge: Forge) {
    val randomValue = forge.anAlphabeticalString()
    val randomList = forge.aList { forge.aStringMatching("pattern") }
}
```

**Location:**
- Fixtures in `testFixtures` source set at: `src/testFixtures/kotlin/com/datadog/android/`
- Utilities and builders shared across multiple tests: `utils/forge/Configurator.kt`, `utils/config/InternalLoggerTestConfiguration.kt`
- Test data factories used by Forge configuration: `src/testFixtures/kotlin/.../forge/`

## Coverage

**Requirements:** Coverage measured with Kover 0.7.6 (no hard minimum enforced by build)

**View Coverage:**
```bash
./gradlew koverXmlReportRelease              # Generate XML report for specific module
./gradlew koverReportAll                     # Generate all coverage reports
./gradlew koverReportFeatures                # Coverage for features modules only
./gradlew koverReportIntegrations            # Coverage for integrations modules only
# Reports generated to: build/reports/kover/
```

## Test Types

**Unit Tests:**
- Scope: Single class functionality in isolation
- Location: `src/test/kotlin/` - runs on JVM with Robolectric for Android dependencies
- Approach: Mocks external dependencies, real instances of tested class
- Examples: `ConfigurationBuilderTest.kt`, `HostsSanitizerTest.kt`, `DatadogDataConstraintsTest.kt`
- Robolectric configured: Unmock Android framework classes needed for unit testing

**Integration Tests:**
- Scope: Feature interactions, component collaboration
- Location: `src/androidTest/kotlin/` - runs on Android device/emulator
- Approach: Minimal mocking, tests real Android components
- Not extensively documented in codebase; primarily unit tests are emphasized

**E2E Tests:**
- Framework: Not used (instrumented integration tests in `instrumented/integration/`)
- Location: `instrumented/integration/src/main/kotlin/` and `src/androidTest/`
- Approach: Full app scenarios in test activities

## Common Patterns

**Async Testing:**
```kotlin
// JUnit 5 timeout annotation
@Test
@Timeout(value = 5, unit = TimeUnit.SECONDS)
fun testAsyncOperation() {
    // Use CountDownLatch or similar for waiting
    val latch = CountDownLatch(1)
    // ... perform async operation
    latch.await(2, TimeUnit.SECONDS)
}
```

**Error Testing:**
```kotlin
// Test expected exceptions
@Test
fun `M throw exception W invalidInput()`() {
    assertThatThrownBy {
        functionUnderTest(invalidInput)
    }.isInstanceOf(IllegalArgumentException::class.java)
}

// Test error logging
@Test
fun `M log error W sanitizeHosts { malformed hostname }`(
    @StringForgery(regex = "...malformed...") hosts: List<String>
) {
    // When
    testedSanitizer.sanitizeHosts(hosts, fakeFeature)

    // Then
    argumentCaptor<() -> String> {
        verify(logger.mockInternalLogger, times(hosts.size)).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.USER),
            capture(),
            any<MalformedURLException>(),
            eq(false),
            eq(null)
        )
        assertThat(allValues.map { it() })
            .containsExactlyInAnyOrderElementsOf(expectedMessages)
    }
}
```

**Parameterized Tests:**
```kotlin
@ParameterizedTest
@EnumSource(ConsentStatus::class)
fun `M handle all consent states W processConsent()`(consent: ConsentStatus) {
    // Test repeated with each enum value
}
```

**Named Test Methods:**
```kotlin
// Backtick-wrapped names with M/W pattern: Meaning/When
@Test
fun `M return list when provided valid hosts W sanitizeHosts()`() { }

@Test
fun `M filter out invalid entries W sanitizeHosts { using URLs }`() { }

@Test
fun `M log error W sanitizeHosts { malformed ip address }`() { }
```

## Extension Configuration

**JUnit 5 Extensions:**
- `MockitoExtension`: Provides mock injection and verification
- `ForgeExtension`: Data generation via `@Forgery` and related annotations
- `TestConfigurationExtension`: Custom test setup for Datadog SDK
- Applied via `@Extensions` annotation on test class

**Forge Configuration:**
```kotlin
@ForgeConfiguration(value = Configurator::class)
internal class SomeTest { }

// Configurator provides custom forge setup for data generation
```

**Custom Configuration:**
```kotlin
companion object {
    val logger = InternalLoggerTestConfiguration()

    @TestConfigurationsProvider
    @JvmStatic
    fun getTestConfigurations(): List<TestConfiguration> {
        return listOf(logger)
    }
}
```

## Test Isolation

**Best Practices:**
- Each test independent: No test should depend on another
- Setup in `@BeforeEach`: New instances for each test
- Cleanup in `@AfterEach`: Release resources, reset state
- No shared mutable state between tests
- Use `@Mock` for fresh mocks per test, or `mock()` inline for test-specific mocks
- Example from `DatadogCoreTest.kt`:
  ```kotlin
  @BeforeEach
  fun setUp() {
      testedCore = DatadogCore(...)  // Fresh instance
  }

  @AfterEach
  fun tearDown() {
      testedCore.stop()  // Cleanup
  }
  ```

## Detekt Test Configuration

**File:** `detekt_test_pyramid.yml` (defined but `active: false` in current config)

**Strategy:** Zero-tolerance for issues (`maxIssues: 0`)
- Custom Datadog rules apply to test code as well
- Tests excluded from certain checks: `excludes` in `detekt_custom_general.yml` excludes `**/test/**`, `**/testDebug/**`, `**/androidTest/**`

---

*Testing analysis: 2026-01-21*
