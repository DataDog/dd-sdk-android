# Coding Conventions

**Analysis Date:** 2026-01-21

## Naming Patterns

**Files:**
- PascalCase for Kotlin class/object files: `Configuration.kt`, `DatadogCore.kt`, `HostsSanitizer.kt`
- PascalCase with "Test" suffix for test files: `ConfigurationBuilderTest.kt`, `HostsSanitizerTest.kt`
- Suffix conventions: `*Test.kt` for unit tests, `*AndroidTest.kt` for instrumented tests

**Functions:**
- camelCase for all public and private functions: `sanitizeHosts()`, `validateTags()`, `registerDefaultNetworkCallback()`
- Backtick-wrapped names allowed for test methods using `M/W` convention: `` `M return the whole hosts list W sanitizeHosts { valid IPs list provided }` ``
- Internal functions marked with `internal` modifier: `internal fun convertTag()`
- Private functions marked with `private` modifier: `private fun convertAttributeKey()`

**Variables:**
- camelCase for all variables: `lastNetworkInfo`, `clientToken`, `testedCore`
- Constants in UPPER_SNAKE_CASE in companion objects: `MAX_TAG_LENGTH`, `MAX_TAG_COUNT`, `MAX_ATTR_COUNT`
- Mutable collections prefixed with `mutable`: `mutableSetOf()`, `mutableListOf()`, `mutableMapOf()`
- Private/internal members prefixed with underscores not used; instead use `private` modifier

**Types:**
- PascalCase for all type names: `Configuration`, `DatadogDataConstraints`, `NetworkInfo`
- Generic type parameters in PascalCase: `<T : Any>`, `<T>` where T is a type parameter
- Enum entries in UPPER_SNAKE_CASE: `NETWORK_NOT_CONNECTED`, `NETWORK_OTHER`
- Data classes used for value objects: `data class Configuration(...)`
- Sealed classes used for controlled hierarchies: `sealed class Result`

## Code Style

**Formatting:**
- ktlint with Android Studio code style (configured in `.editorconfig`)
- Max line length: 120 characters
- Trailing commas: Disabled (`ij_kotlin_allow_trailing_comma_on_call_site = false`)
- Imports layout: `*,java.**,javax.**,kotlin.**,^` (grouped order as specified)
- No trailing commas on call sites or property definitions

**Linting:**
- Detekt active with custom Datadog rules configured in `detekt_custom_general.yml`
- Max issues: 0 (strict zero-tolerance policy)
- Warnings treated as errors: `warningsAsErrors: true`
- Custom detekt rules for code quality:
  - `PackageNameVisibility`: Enforces proper package visibility (respects `@InternalApi`)
  - `PreferTimeProvider`: Requires use of `TimeProvider` abstraction instead of direct time calls
  - `ThreadSafety`: Validates thread context switches via approved methods like `submitSafe`, `executeSafe`, `scheduleSafe`
  - `TodoWithoutTask`: TODOs must have task IDs (blocks deprecated prefixes: RUMM, REPLAY)
  - `UnsafeThirdPartyFunctionCall`: Requires wrapping third-party calls in `@Suppress("UnsafeThirdPartyFunctionCall")`

**Suppressions:**
- Used sparingly with `@Suppress` annotation to justify known issues
- Common suppressions: `"TooManyFunctions"`, `"TooGenericExceptionCaught"`, `"FunctionMaxLength"`, `"StringLiteralDuplication"`, `"UnsafeThirdPartyFunctionCall"`
- File-level suppressions at top: `@file:Suppress("StringLiteralDuplication")`

## Import Organization

**Order:**
1. Package declaration
2. Internal imports (project/module): `com.datadog.*`
3. Standard library: `java.*`, `javax.*`, `kotlin.*`
4. Third-party: `android.*`, `androidx.*`, `okhttp3.*`, etc.
5. Relative imports: `^` (same package, placed last)

**Path Aliases:**
- No explicit aliases used; fully qualified package names preferred
- Wildcard imports avoided; individual imports required
- Example imports from tests:
  ```kotlin
  import org.junit.jupiter.api.Test
  import org.mockito.junit.jupiter.MockitoExtension
  import fr.xgouchet.elmyr.annotation.StringForgery
  import com.datadog.android.core.configuration.Configuration
  ```

## Error Handling

**Patterns:**
- Specific exception handling: Catch `SecurityException` separately from general `Exception`
- Pattern: Try-catch with inline recovery or fallback values
- Example from `CallbackNetworkInfoProvider.kt`:
  ```kotlin
  try {
      connMgr.registerDefaultNetworkCallback(this)
      // ...
  } catch (e: SecurityException) {
      // RUMM-852 specific issue documented
      internalLogger.log(...)
      lastNetworkInfo = NetworkInfo(NetworkInfo.Connectivity.NETWORK_OTHER)
  } catch (e: Exception) {
      // RUMM-918 specific issue documented
      internalLogger.log(...)
      lastNetworkInfo = NetworkInfo(NetworkInfo.Connectivity.NETWORK_OTHER)
  }
  ```
- Throwables logged via `internalLogger.log(level, target, messageBuilder, exception)`
- No bare `throw` statements; exceptions logged before recovery
- Error comments include issue tracking: `// RUMM-XXX Issue description`

## Logging

**Framework:** Custom `InternalLogger` abstraction

**Patterns:**
- Three log targets: `USER`, `MAINTAINER`, `TELEMETRY`
- Log via lambda (not eagerly evaluated): `internalLogger.log(InternalLogger.Level.ERROR, target, { "message" })`
- Log levels: `VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`
- Messages as lazy lambdas: `{ "formatted: $value" }` evaluates only if logged
- Onlyonce flag for duplicate suppression: `onlyOnce = true`
- Example from `DatadogDataConstraints.kt`:
  ```kotlin
  internalLogger.log(
      InternalLogger.Level.WARN,
      InternalLogger.Target.USER,
      { "tag \"$it\" was modified to \"$tag\" to match our constraints." },
      onlyOnce = true
  )
  ```
- Second parameter (target) determines audience: USER (end-developer facing), MAINTAINER (internal SDK team), TELEMETRY (metrics)

## Comments

**When to Comment:**
- Algorithm explanations: "prefix = "a.b" => dotCount = 1+1 ("a.b." + key)"
- Bug references with tracking IDs: `// RUMM-918 Issue description and workaround`
- Non-obvious transformations: Comments before complex regex or string manipulations
- Intent clarification for unusual patterns: `// We need this in case attributes are added from JAVA code and null key may be passed`
- Region markers for organized sections: `// region SectionName` and `// endregion`

**No Comments For:**
- Self-documenting code with clear naming
- Simple getter/setter logic
- Method parameter documentation (use KDoc instead)

**JSDoc/KDoc:**
- Used for public APIs and classes: Documented in triple-slash comments `/** */`
- Parameter documentation: `@param` tags for function parameters
- Return documentation: `@return` tags explaining return values
- InternalApi annotation: `@com.datadog.android.lint.InternalApi` for internal-only APIs
- Example from `Configuration.kt`:
  ```kotlin
  /**
   * An object describing the configuration of the Datadog SDK.
   * This is necessary to initialize the SDK with the [Datadog.initialize] method.
   */
  data class Configuration(...)
  ```

## Function Design

**Size:**
- Prefer compact functions (50-100 lines max for main logic)
- Detekt limits enforced; `@Suppress("FunctionMaxLength")` used when exceeded
- Large functions split into private helper functions
- Example: `DatadogDataConstraints.kt` has `validateTags()`, `validateAttributes()`, `validateTimings()` as separate focused functions

**Parameters:**
- Explicit parameters preferred over builder pattern for internal functions
- Lambda parameters used for callbacks: `userLogHandlerFactory: () -> LogcatLogHandler = { ... }`
- Default parameters for optional values: `val variant: String = NO_VARIANT`
- Named parameters encouraged when calling: `Configuration.Builder(..., variant = "", service = null)`

**Return Values:**
- Nullable returns used sparingly with clear intent
- Non-null collections preferred: Return empty `emptyMap()`, `emptyList()` instead of null
- Data classes for multi-value returns: `data class Core(...)`
- Sealed hierarchies for error cases when applicable

## Module Design

**Exports:**
- Single public entry point per module where possible
- Public API surfaces clearly marked: `class Configuration(...)` vs `internal class DatadogCore`
- `@InternalApi` annotation used for implementation details exposed to related modules

**Barrel Files:**
- Package-level imports organized at top of `kt` files
- No dedicated barrel/index files; imports done explicitly
- Public API documented in main class or interface

**Visibility Modifiers:**
- Default to `private` for all new members
- Promote to `internal` if needed by same module
- Promote to `public` only for intended API surface
- Example hierarchy:
  ```kotlin
  public class Configuration {           // Public API
      internal data class Core(...)      // Module-internal structure
      private fun helper() { ... }       // Private implementation detail
  }
  ```

---

*Convention analysis: 2026-01-21*
