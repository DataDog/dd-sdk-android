---
name: sdk-tests
description: >-
  Unit-test conventions for dd-sdk-android. Use when creating, editing, or reviewing
  Kotlin unit tests and test fixtures with JUnit 5, Mockito, Elmyr Forge, and AssertJ.
when_to_use: >-
  Writing new tests, adding regression coverage, fixing failing tests,
  or refactoring test fixtures and assertions.
paths:
  - "**/src/test/**/*.kt"
  - "**/src/testFixtures/**/*.kt"
  - "**/*Test.kt"
---

# dd-sdk-android Test Writing Guide

## Stack

- **JUnit 5 (Jupiter)** — test framework
- **Mockito** + `mockito-kotlin` — mocks
- **Elmyr Forge** — random data generation
- **AssertJ** — fluent assertions

## Test class structure

### Required annotations

Mockito only:

```kotlin
@Extensions(
    ExtendWith(MockitoExtension::class),
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class MyClassTest {
    // ...
}
```

Forge + Mockito (most tests). Use the module's existing forge configurator (see Elmyr Forge in `AGENTS.md`): find it via `@ForgeConfiguration(...)` in the module's tests and register new `ForgeryFactory` implementations there; don't create a new one.

```kotlin
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MyClassTest {
    // ...
}
```

Drop `MockitoExtension` and `@MockitoSettings` if the test has no mocks.

Don't add mutable static fields to production code (see Threading in `AGENTS.md`). If a test must touch existing global state (`Datadog`, `GlobalRumMonitor`), reset it in `@AfterEach` (see [Setup / teardown](#setup--teardown)).

### Visibility

Test classes are `internal`.

## Naming convention

**Do not use `region` comments in tests.**

**No inline comments in test bodies** — the only allowed comments are the `// Given`, `// When`, `// Then`, and `// When / Then` block markers. Do not add any other explanatory, descriptive, or section comments inside a test method.

Backtick names follow the **M…W…** pattern:
- **M** = "Must" (expected outcome)
- **W** = "When" (action / condition)

```kotlin
@Test
fun `M return true W registerIfAbsent(monitor)`() { /* ... */ }

@Test
fun `M return false W registerIfAbsent(monitor) twice {same sdkCore}`() { /* ... */ }

@Test
fun `M log a warning W get() {no monitor registered}`() { /* ... */ }

@Test
fun `M delegate to request W start()`() { /* ... */ }

@Test
fun `M return null W register+unregister+getInstance {default name name}`() { /* ... */ }
```

**Method-name consistency is mandatory.** The method(s) in the `W` part of the test name must match the actual methods called in the `// When` block. When renaming source methods, update test names to match.

```kotlin
// Good
fun `M return info and remove W remove()`() {
    // When
    val result = testedRegistry.remove(mockCall)
}

// Bad — test name says "unregister", real method is "remove"
fun `M return info and remove W unregister()`() {
    // When
    val result = testedRegistry.remove(mockCall)
}
```

## Test body — Given / When / Then

```kotlin
@Test
fun `M return monitor W registerIfAbsent(monitor) + get() {same sdkCore}`() {
    // Given (optional — setup beyond @BeforeEach)
    val additionalSetup = ...

    // When
    GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
    val result = GlobalRumMonitor.get(mockSdkCore)

    // Then
    assertThat(result).isSameAs(mockRumMonitor)
}
```

Single-line tests use `// When / Then`:

```kotlin
@Test
fun `M return null W foo`() {
    // When / Then
    assertThat(testedInstance.foo()).isNull()
}
```

## Mocking

### Declaration

```kotlin
@Mock
lateinit var mockRumMonitor: RumMonitor

@Mock
lateinit var mockSdkCore: FeatureSdkCore

@Mock
lateinit var mockInternalLogger: InternalLogger
```

### Naming

- Mock vars → `mock` prefix: `mockSdkCore`, `mockLogger`, `mockBuilder`
- Fake-data vars → `fake` prefix: `fakeRequestInfo`, `fakeUrl`, `fakeName`

### Setup (mockito-kotlin DSL)

```kotlin
@BeforeEach
fun `set up`() {
    whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
}
```

### Use `mock { }` for 3+ stubs on the same mock

```kotlin
// Good
mockBuilder = mock {
    on { setUrl(any()) } doReturn it
    on { setMethod(any()) } doReturn it
    on { build() } doReturn mockResult
}

// Bad — 3 separate whenever calls for the same mock
whenever(mockBuilder.setUrl(any())) doReturn mockBuilder
whenever(mockBuilder.setMethod(any())) doReturn mockBuilder
whenever(mockBuilder.build()) doReturn mockResult
```

### Verification

```kotlin
verify(mockBuiltRequest).start()
verify(mockRumResourceInstrumentation).startResource(any<CronetHttpRequestInfo>())
```

### Prefer `verifyNoInteractions` over `verify(..., never())`

When the only assertion on a mock is that it received no calls at all, use `verifyNoInteractions` instead of one or more `verify(mock, never())` calls:

```kotlin
// Good
verifyNoInteractions(mockDataWriter)

// Bad — only checks one method; also verbose
verify(mockDataWriter, never()).write(any(), any(), any())
```

Use `verify(mock, never()).method(...)` only when you need to assert that a *specific* method was not called while other methods on the same mock *were* called.

### Prefer specific matchers over `any()` in `verify`

Use `eq()` / `isA()` when the expected value is known. Reserve `any()` for `never()` checks.

```kotlin
// Good
verify(mockInstrumentation).onResponseSucceeded(eq(mockTraceState), isA<HttpResponseInfo>())
verify(mockDelegate).onRedirectReceived(capture(), eq(mockResponseInfo), eq(fakeUrl))
verify(mockInstrumentation, never()).onResponseSucceeded(any(), any())

// Bad
verify(mockInstrumentation).onResponseSucceeded(any(), capture())
```

### Logger verification — always use `verifyLog` / `verifyApiUsage`

**Never call `verify(mockInternalLogger).log(...)` directly.** Use the `InternalLoggerUtils` extensions:

- Core (shared via testFixtures): `dd-sdk-android-core/src/testFixtures/kotlin/com/datadog/android/utils/InternalLoggerUtils.kt`
- Per-module copies, e.g. `integrations/dd-sdk-android-cronet/src/test/kotlin/com/datadog/android/api/InternalLoggerUtils.kt`

Use whichever copy is already imported in the test module.

#### `verifyLog`

```kotlin
mockInternalLogger.verifyLog(
    InternalLogger.Level.WARN,
    InternalLogger.Target.USER,
    "Expected message"
)

mockInternalLogger.verifyLog(
    InternalLogger.Level.ERROR,
    InternalLogger.Target.USER,
    "Expected message",
    throwable = fakeThrowable
)

mockInternalLogger.verifyLog(
    InternalLogger.Level.ERROR,
    InternalLogger.Target.USER,
    "Expected message",
    throwableClass = IllegalStateException::class.java
)

mockInternalLogger.verifyLog(
    InternalLogger.Level.ERROR,
    listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
    "Expected message"
)

mockInternalLogger.verifyLog(
    InternalLogger.Level.WARN,
    InternalLogger.Target.USER,
    "Expected message",
    mode = times(2)
)

mockInternalLogger.verifyLog(
    InternalLogger.Level.WARN,
    InternalLogger.Target.USER,
    { it.contains("partial text") }
)
```

#### `verifyApiUsage`

```kotlin
mockInternalLogger.verifyApiUsage(
    expectedApiUsageEvent = InternalTelemetryEvent.ApiUsage.NetworkInstrumentation(...),
    expectedSamplingRate = 15f
)
```

## Forge (random data)

### Annotations

```kotlin
@Test
fun `M log a warning W get() {no monitor registered}`(
    @StringForgery fakeSdkInstanceName: String
) {
    // test
}
```

Available:
- `@StringForgery` — random strings (regex supported: `@StringForgery(regex = "^[a-z0-9]{32}$")`)
- `@BoolForgery`, `@IntForgery` (min/max), `@LongForgery`
- `@Forgery` — inject a forged instance of any registered type
- For URL strings use regex `"http(s?)://[a-z]+\\.com/[a-z]+"`

### `Forge` in `@BeforeEach`

```kotlin
@BeforeEach
fun setup(forge: Forge) {
    fakeRequestInfo = CronetHttpRequestInfo(
        url = forge.aStringMatching("https://[a-z]+\\.com/[a-z]+"),
        method = forge.anElementFrom("GET", "POST", "PUT", "DELETE"),
        headers = emptyMap()
    )
}
```

### Common methods

`forge.aStringMatching(regex)` · `forge.anElementFrom(...)` · `forge.aList { ... }` · `forge.aNullable { ... }` · `forge.anInt(min, max)` · `forge.anAlphaNumericalString()` · `forge.getForgery<T>()`

### Forgery factories

```kotlin
internal class BatchDataForgeryFactory : ForgeryFactory<BatchData> {
    override fun getForgery(forge: Forge): BatchData {
        return BatchData(
            id = BatchId(id = forge.anAlphaNumericalString()),
            data = forge.aList { getForgery() },
            metadata = forge.aNullable {
                forge.aString(forge.anInt(min = 0, max = 32)).toByteArray()
            }
        )
    }
}
```

### `Configurator`

Each module has its own `Configurator`:

```kotlin
internal class Configurator : BaseConfigurator() {
    override fun configure(forge: Forge) {
        super.configure(forge)
        forge.addFactory(MyCustomForgeryFactory())
    }
}
```

Location: `src/test/kotlin/com/datadog/android/*/utils/forge/Configurator.kt`

## Assertions (AssertJ)

```kotlin
import org.assertj.core.api.Assertions.assertThat

assertThat(result).isTrue()
assertThat(result).isFalse()
assertThat(result).isNull()
assertThat(result).isNotNull()
assertThat(result).isSameAs(expected)
assertThat(result).isEqualTo(expected)
assertThat(result).isInstanceOf(NoOpAdvancedRumMonitor::class.java)
```

### Nullability checks

When a test needs to assert that a value is non-null before accessing its members, use `checkNotNull` from the Kotlin standard library. Place the check inside the `// Then` block — it is an assertion, not setup.

```kotlin
// Good
// Then
val result = checkNotNull(testedInstance.getResult()) { "Expected a non-null result" }
assertThat(result.value).isEqualTo(fakeExpected)

// Bad — null check buried in Given/When, not surfaced as an assertion
val result = testedInstance.getResult()!!
assertThat(result.value).isEqualTo(fakeExpected)
```

`checkNotNull` throws `IllegalStateException` with the provided message if the value is null, which produces a clear failure rather than a cryptic `NullPointerException`.

### Asserting that no exception is thrown

If a test's implicit contract is "this call must not throw", make that contract explicit with `assertDoesNotThrow`. Never rely on the test simply completing without error — a silent pass does not communicate intent.

```kotlin
// Good — intent is explicit
assertDoesNotThrow { testedInstance.process(fakeInput) }

// Bad — silent pass does not document that no exception is expected
testedInstance.process(fakeInput)
```

`assertDoesNotThrow` is a JUnit 5 (Jupiter) built-in:

```kotlin
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
```

## Setup / teardown

```kotlin
@BeforeEach
fun `set up`() { /* ... */ }

@AfterEach
fun `tear down`() { /* ... */ }
```

Or plain `setUp()`. Always clean static state in `@AfterEach`:

```kotlin
@AfterEach
fun `tear down`() {
    GlobalRumMonitor.clear()
}
```

## Parametrized tests

Prefer Forge-driven parameters over `Forge` parameter. Use annotations for primitives.

```kotlin
@ParameterizedTest
@MethodSource("provideTestCases")
fun `M return expected W process(input)`(input: String, expected: String) { /* ... */ }

companion object {
    @JvmStatic
    fun provideTestCases() = listOf(
        Arguments.of("input1", "expected1"),
        Arguments.of("input2", "expected2")
    )
}
```

## Repeated tests

```kotlin
@RepeatedTest(16)
fun `M produce valid output W process()`(forge: Forge) { /* ... */ }
```

## File location

Mirror source structure:
- Source: `src/main/kotlin/com/datadog/android/feature/MyClass.kt`
- Test: `src/test/kotlin/com/datadog/android/feature/MyClassTest.kt`

## Best practices

1. **One behavior per test** — single assertion, or related ones testing one behavior
2. **Independent tests** — no shared mutable state between tests
3. **Meaningful fake data** — Forge should produce realistic values
4. **Clean up** — reset static state, clear mocks
5. **Edge cases** — nulls, empty collections, boundary values
6. **Verify interactions** — ensure expected calls happened
7. **`lateinit` for mocks** — lets Mockito inject before initialization
8. **No empty `setUp`** — drop it if there is nothing to set up
9. **DRY** — shared setup → `@BeforeEach`, not duplicated per test
10. **Concise tests** — aim for one line per Given/When/Then block; extract helpers or move to `@BeforeEach`. **Never sacrifice coverage for brevity** — completeness wins.
11. **No `!!` operator** — never use the non-null assertion operator `!!` in tests. Use `checkNotNull()` or `requireNotNull()` from the Kotlin standard library instead, which throw `IllegalStateException`/`IllegalArgumentException` with a meaningful message.

```kotlin
// Good
val value = checkNotNull(maybeNull) { "Expected non-null value" }

// Bad
val value = maybeNull!!
```
