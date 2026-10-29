# Datadog Feature Flags SDK for Android

The Datadog Feature Flags SDK for Android allows you to evaluate feature flags and experiments in your Android application and automatically send flag evaluation data to Datadog for monitoring and analysis.

## Getting started

Add the Datadog Feature Flags SDK to your application's `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-flags:<latest-version>")
    
    // Recommended: RUM integration drives analysis and enriches RUM session data
    implementation("com.datadoghq:dd-sdk-android-rum:<latest-version>")
}
```

### Initial setup

Before enabling the Feature Flags feature, you must first initialize the Datadog SDK. See the [Datadog Android SDK setup documentation][1] for details.

```kotlin
// Initialize the core Datadog SDK first
val coreConfiguration = Configuration.Builder(
    clientToken = "<YOUR_CLIENT_TOKEN>",
    env = "<YOUR_ENVIRONMENT>",
    variant = "<YOUR_APP_VARIANT>"
)
    .build()

Datadog.initialize(this, coreConfiguration, trackingConsent)
```

### (Recommended) Enable RUM in your application

RUM session data is enriched with Feature Flag evaluations and is used to drive the analysis for your Feature Flags. Skip this step if RUM is already configured
or you are opting out of the RUM integration.

```kotlin
val rumConfig = RumConfiguration.Builder(applicationId = "<YOUR_RUM_APPLICATION_ID>")
    .build()
    
Rum.enable(rumConfig)
```

If RUM is not enabled, the Flags SDK works normally but flag evaluations will not appear in RUM views.

## Setup

### Enable the Feature Flags Feature

After initializing the Datadog SDK, enable the Feature Flags feature:

```kotlin
val flagsConfig = FlagsConfiguration.Builder().build()
Flags.enable(flagsConfig)
```

### Configuration options

The `FlagsConfiguration.Builder` supports the following options:

#### Disable RUM integration

By default, flag evaluations are automatically sent to RUM (Real User Monitoring) and attached to the current view if RUM is enabled. You can disable this integration:

```kotlin
val flagsConfig = FlagsConfiguration.Builder()
    .rumIntegrationEnabled(false)
    .build()
```

**Note:** This setting only has an effect if you have enabled RUM (see [Initial setup](#initial-setup) section). If RUM is not enabled, flag evaluations are not sent to RUM regardless of this setting.

#### Disable exposure tracking

By default, flag evaluations are tracked and sent to Datadog's exposure intake endpoint. You can disable this:

```kotlin
val flagsConfig = FlagsConfiguration.Builder()
    .trackExposures(false)
    .build()
```

#### Configure custom endpoints

For testing or proxy purposes, you can configure custom endpoints:

```kotlin
val flagsConfig = FlagsConfiguration.Builder()
    .useCustomFlagEndpoint("https://your-proxy.example.com/flags")
    .useCustomExposureEndpoint("https://your-proxy.example.com/exposure")
    .build()
```

## Use the Feature Flags SDK

### Create a Flags client

After enabling the Feature Flags feature, create a `FlagsClient` to evaluate flags:

```kotlin
// Create a default client
val client = FlagsClient.Builder().build()

// Or create a named client for specific use cases
val analyticsClient = FlagsClient.Builder("analytics").build()
```

### Exclude a subject from experimentation

Configure the client to honor `dd_experimentation_opt_out_at`:

```kotlin
val client = FlagsClient.Builder()
    .experimentationOptOutMode(ExperimentationOptOutMode.IMMEDIATE)
    .build()

client.setEvaluationContext(
    EvaluationContext(
        targetingKey = "user-123",
        attributes = mapOf("dd_experimentation_opt_out_at" to "2026-10-21T10:00:00Z")
    ),
    object : EvaluationContextCallback {
        override fun onSuccess() {
            val enabled = client.resolveBooleanValue("new-feature", false)
            // Use the resolved value here.
        }

        override fun onFailure(error: Throwable) {
            // Keep the application's default behavior and handle the error here.
        }
    }
)
```

`setEvaluationContext` is asynchronous. Resolve the flag after its success callback.
Callbacks run on a background thread. Dispatch UI changes to the main thread.
A newer context can replace the assignments before a callback runs. In enabled modes, a request replaced
before its result is processed fails with `FlagsContextSupersededException`, unless its timeout already completed it.
Treat this exception as cancellation. OpenFeature maps it to coroutine cancellation instead of a provider error.
This applies whether the superseded fetch succeeds or fails. A result processed before replacement keeps its original outcome.
In `DISABLED`, previous assignments remain available and superseded requests retain their ordinary completion behavior.

| Mode | Behavior when the subject timestamp is present |
| --- | --- |
| `DISABLED` (default) | Ignore the timestamp. |
| `IMMEDIATE` | Exclude all experiment allocations, including experiments already in progress. |
| `FUTURE_EXPERIMENTS_ONLY` | Continue experiments activated before the timestamp. Exclude newer experiments and those with unknown activation times. |

Provide the customer's stored opt-out timestamp in the format below. Do not replace it with the current time on each launch.
In `IMMEDIATE` mode, any valid timestamp, including a future timestamp, excludes the subject from experiments now.
In `FUTURE_EXPERIMENTS_ONLY` mode, a future timestamp keeps experiments activated before that timestamp eligible.
Omit the attribute to permit experimentation.
Invalid timestamps return application defaults with `INVALID_CONTEXT` and do not send an assignment request.
An absent timestamp sends an ordinary assignment request without `evaluation_options`, including in enabled modes.

Timestamps use Gregorian calendar dates with years `0000` through `9999`, both before and after UTC normalization.
Use `YYYY-MM-DDTHH:mm:ss`, optional fractional seconds, and `Z` or a numeric `±HH:mm` offset
(hours `00`–`23`, minutes `00`–`59`). Lowercase `t` and `z` are accepted. Fractions beyond nine digits
are truncated without rounding, and comparisons preserve nanosecond precision. Leap seconds, invalid dates,
whitespace and named timezones are rejected.

The backend skips experiment allocations and continues through ordinary feature allocations.
The SDK requires the backend to acknowledge exclusion before using the response.
When the subject has no effective opt-out policy, responses explicitly marked as filtered are rejected.
Future mode requires the matching mode and exact cutoff in the acknowledgement.
In either enabled mode, assignments from a different context are unavailable, including for subjects without the timestamp attribute.
At startup, call `setEvaluationContext` with the matching context before the client can use persisted assignments.
Until matching assignments are available, evaluation returns the application default with `PROVIDER_NOT_READY`.
A malformed timestamp returns `INVALID_CONTEXT` instead. With `DISABLED`, persisted and previous assignments remain available.
Previously accepted assignments can be reused only for the same context, cutoff, and mode.
Each mode retains a separate disk cache. Changing modes does not delete another mode's cache.
Switching back can reuse that mode's older snapshot. In `DISABLED`, it is served as soon as persisted state loads,
for whichever subject it belonged to, until a successful context fetch replaces it. A failed fetch keeps it.
Call `setEvaluationContext` for the active subject; mode changes do not provide cache eviction.
`onFirstFlags` can report cached keys before an enabled-mode client has a matching requested context.
Those keys do not indicate readable values, and setting the context does not emit a second first-flags notification.
Evaluation telemetry is independent of experimentation opt-out.
Enable this option only after acknowledgement, ordinary fallback, and activation metadata are available on the customer's site.
If you use `useCustomFlagEndpoint`, the endpoint must preserve `data.attributes.evaluation_options` in both
requests and responses. Missing acknowledgement causes defaults for opted-out subjects without matching cached assignments.
Without acknowledgement or matching cached assignments, evaluation returns defaults with `PROVIDER_NOT_READY`.
A later successful `setEvaluationContext` call recovers the client. The SDK does not retry this operation automatically.

`ErrorCode.INVALID_CONTEXT` is a new public enum constant. Exhaustive Kotlin `when` expressions must handle it.
The context callback reports malformed timestamps through `FlagsInvalidContextException`.

Immediate withdrawal can change an enrolled subject's experience. Account for this change in experiment analysis.

### Control evaluation telemetry

Enable the local telemetry control when creating a client:

```kotlin
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.setEvaluationTelemetryOptOut

val client = FlagsClient.Builder("app")
    .enableEvaluationTelemetryOptOutParameter(true)
    .build()

// Your application owns this decision.
client.setEvaluationTelemetryOptOut(customerOptedOut)
```

When `customerOptedOut` is `true`, the client suppresses new evaluation events, counts, and automatic RUM flag annotations. Flag values and exposure logging remain unchanged.

The native targeting context contains strings. Use the typed setter above; a string `"true"` in the context does not activate opt-out. The [OpenFeature provider](../dd-sdk-android-flags-openfeature/README.md#control-evaluation-telemetry) accepts a boolean context attribute instead.

The `dd_evaluation_telemetry_opt_out` context attribute remains available for targeting and exposure reporting. Changing it through `setEvaluationContext` uses the normal assignment refresh path. Targeting rules can change assignments, and normal exposure deduplication applies.

The setter applies locally without fetching assignments or clearing cached values. Update the decision when the subject or your policy changes. Set it to `false` to resume evaluation telemetry. Activation defaults to disabled. Each named client retains its original configuration.

Previously recorded events, existing RUM annotations, manual annotations, and unrelated telemetry remain unchanged. Exposure events can still contain subject data. This control does not replace SDK tracking consent.

### Set evaluation context

Before evaluating flags, set the evaluation context with a targeting key and optional attributes:

```kotlin
val context = EvaluationContext(
    targetingKey = "user-123",
    attributes = mapOf(
        "email" to "user@example.com",
        "plan" to "premium",
        "age" to "25"
    )
)

client.setEvaluationContext(context)
```

**Notes**
- The targeting key must be consistent for the same user or entity to ensure consistent flag evaluation across requests. Common targeting keys include user ID, device ID, or session ID.
- For anonymous or unauthenticated users, use a **persistent UUID** as the targeting key:
  - **Proper traffic splitting**: A unique identifier ensures users are distributed correctly across flag variations.
  - **Consistent experience**: Persistence means the same user always sees the same flag values (consistent bucketing).
  - Generate the UUID once and persist it locally (for example, in `SharedPreferences`).
  - Transition to a user ID when the user authenticates.
- All attribute values must be strings. Convert numbers, booleans, and other types to strings before passing them.

### Evaluate feature flags

The `FlagsClient` provides two ways to resolve flag values:

- **Convenience methods** (`resolveBooleanValue`, `resolveStringValue`, etc.): Simple methods that return just the value
- **Detailed resolution method** (`resolve`): Returns comprehensive resolution details, including error information and metadata

#### Convenience methods

Use these methods when you only need the flag value:

**Boolean flags**
```kotlin
val isNewFeatureEnabled = client.resolveBooleanValue("new-feature-enabled", false)
```

**String flags**
```kotlin
val theme = client.resolveStringValue("app-theme", "light")
```

**Numeric flags**
```kotlin
// Integer values
val maxRetries = client.resolveIntValue("max-retry-count", 3)

// Double values
val discountPercentage = client.resolveDoubleValue("discount-rate", 0.0)
```

**Structured flags**
```kotlin
val defaultConfig = JSONObject("""{"timeout": 30, "retries": 3}""")
val config = client.resolveStructureValue("api-config", defaultConfig)

val timeout = config.getInt("timeout")
val retries = config.getInt("retries")
```

#### Detailed resolution method

Use the `resolve()` method when you need additional information about flag resolution, such as:
- The variant identifier (for example, "control", "treatment")
- The reason for the resolved value (for example, `TARGETING_MATCH`, `DEFAULT`, `ERROR`)
- Error codes and messages for debugging
- Flag metadata for analytics

```kotlin
val result = client.resolve("feature-enabled", false)

// Access the resolved value
val featureEnabled = result.value

// Check for errors
if (result.errorCode != null) {
    println("Flag resolution failed: ${result.errorMessage}")
} else {
    println("Flag resolved successfully")
    
    // Access variant information
    result.variant?.let { variant ->
        println("Variant: $variant")
    }
    
    // Access resolution reason
    result.reason?.let { reason ->
        println("Reason: $reason")
    }
    
    // Access flag metadata
    result.flagMetadata?.forEach { (key, value) ->
        println("Metadata: $key = $value")
    }
}
```

##### Detailed resolution properties
- `value: T` - The resolved flag value (always present, either from evaluation or default)
- `variant: String?` - Optional identifier for the resolved variant
- `reason: String?` - Optional explanation of why this value was resolved
- `errorCode: ErrorCode?` - Optional error code (null indicates success)
- `errorMessage: String?` - Optional human-readable error message
- `flagMetadata: Map<String, Any>?` - Optional metadata associated with the flag

##### Error codes
- `FLAG_NOT_FOUND` - The flag could not be found
- `PARSE_ERROR` - Error parsing the flag value
- `TYPE_MISMATCH` - The flag type doesn't match the expected type
- `TARGETING_KEY_MISSING` - No targeting key was provided
- `INVALID_CONTEXT` - The evaluation context is invalid
- `PROVIDER_NOT_READY` - The provider is not yet ready
- `PROVIDER_FATAL` - The provider encountered a fatal error
- `GENERAL` - A general error occurred

### Retrieve existing clients

You can retrieve a previously created client by name:

```kotlin
// Retrieve the default client
val client = FlagsClient.get()

// Retrieve a named client
val analyticsClient = FlagsClient.get("analytics")
```

**Note:** If you call `get()` before calling `build()` for that client name, a no-op client is returned that always returns default values and logs an error.

### Wait for the first flags

`client.onFirstFlags` notifies each registration once, when the client installs its first flags from disk
cache or the network. This is one notification per registration, containing all keys in that first
accepted configuration (possibly empty), not one callback per flag or an inventory of all server-side flags.
For an Activity that owns a `client`:

```kotlin
import com.datadog.android.flags.FlagsSubscription
import timber.log.Timber

private var firstFlagsSubscription: FlagsSubscription? = null

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    firstFlagsSubscription = client.onFirstFlags { event ->
        Timber.i("First installed flag keys: %s", event.flagsChanged)
    }
}

override fun onDestroy() {
    firstFlagsSubscription?.unsubscribe()
    super.onDestroy()
}
```

- An available first result replays from retained memory without I/O, synchronously before registration
  returns. Pending callbacks run
  on a dedicated background thread. Dispatch UI work to the main thread.
- Every registration receives the retained first result, even after later updates. Evaluations read
  current flags, which may have changed since the event. Delivery order across registrations is not
  guaranteed: a later registration may be notified before an earlier pending one.
- Missing or unreadable cache and malformed network responses do not trigger the callback. A valid
  empty configuration does trigger it with an empty key list.
- Unsubscription is thread-safe and idempotent and releases pending captures. A callback already
  claimed for delivery may still run; cancellation does not interrupt it or undo synchronous replay.
- This is not a readiness signal. In enabled opt-out modes, cached keys can arrive before any flags are readable.
  Call `setEvaluationContext` and use `client.state` to track readiness. No second first-flags event follows context registration.
- Ordinary callback exceptions are logged and isolated.

Use `client.onFirstFlags { event -> ... }` from Kotlin or
`client.onFirstFlags(event -> { ... })` from Java. The client retains the first installed keys
and creates a separate event with a key-list copy for each delivery. Evaluations inside the callback
read its current flags. Custom `FlagsClient` implementations
must implement `onFirstFlags`; Kotlin interface delegation forwards it to the delegate.

The listener and subscription are Kotlin functional interfaces: `FlagsClientEventListener.onEvent`
and `FlagsSubscription.unsubscribe`.
`FlagsClientEvent` is constructed internally by the SDK, snapshots its key list, and currently supports
`CONFIGURATION_CHANGED`. Applications receive events through the listener.

The [Kotlin sample](../../sample/kotlin/src/main/kotlin/com/datadog/android/sample/SampleApplication.kt)
registers an application-lifetime callback, logs keys, and evaluates `"my-flag-key"` with default `false`.

## Integration with RUM

When RUM is enabled in your application and RUM integration is enabled in the Flags configuration (default), flag evaluations are automatically:
- Attached to the current RUM view
- Visible in the Datadog RUM dashboard
- Associated with user sessions for analysis

This allows you to correlate feature flag usage with application performance, errors, and user behavior.

## Prerequisites for RUM integration
1. Add the `dd-sdk-android-rum` dependency to your project
2. Enable RUM before initializing the Flags feature (see [Initial setup](#initial-setup) section)
3. Ensure `rumIntegrationEnabled` is set to `true` in your `FlagsConfiguration` (this is the default)

If RUM is not enabled, the Flags SDK will continue to work normally, but flag evaluations will not appear in RUM views.

## Best practices

- **Consistent targeting keys**: Use consistent targeting keys (for example, user ID) to ensure users see consistent feature flag values across sessions.
- **Provide meaningful defaults**: Always provide sensible default values that maintain core functionality if flag evaluation fails.
- **Set context early**: Set the evaluation context as early as possible in your application lifecycle, typically after user authentication.
- **Use named clients**: Use named clients if necessary to organize flags by domain (for example, "analytics", "ui", "experiments")
- **Convert values to strings**: Remember to convert all attribute values to strings before passing them to `EvaluationContext`.

## Further reading

For more information on Feature Flags in Datadog, see the [official Feature Flags documentation][2].

[1]: https://docs.datadoghq.com/real_user_monitoring/application_monitoring/android/setup
[2]: https://docs.datadoghq.com/getting_started/feature_flags/
