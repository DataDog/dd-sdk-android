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

### Choose initialization readiness

`ClientReadyPolicy.NETWORK` is the default: initialization waits for the initial network attempt. If it fails, an installed configuration can satisfy initialization; if disk loading is still pending, initialization waits for that result or the configured initialization deadline. Initialization fails when both sources are exhausted without a configuration.

Use `CACHE_OR_NETWORK` to finish initialization as soon as a valid disk configuration is installed. The network request continues. Any valid installed configuration qualifies, including an empty configuration or one belonging to a previous context. Native state is `Stale` for disk-backed availability and becomes `Ready` after an accepted network response. Successful initialization does not require state `Ready`. This is a Datadog convention: disk data has not been revalidated during this initialization epoch; disk origin alone does not require OpenFeature STALE.

After enabling Flags, create the public client and register a state listener:

```kotlin
val client = FlagsClient.Builder("checkout")
    .clientReadyPolicy(ClientReadyPolicy.CACHE_OR_NETWORK)
    .build()

val listener = object : FlagsStateListener {
    override fun onStateChanged(newState: FlagsClientState) {
        // Observe availability/freshness here; dispatch UI work to the main thread.
        // Resolve a flag at its actual point of use rather than evaluating all flags.
        println("Flags state: $newState")
    }
}
client.state.addListener(listener) // Immediately receives the current state.

client.setEvaluationContext(
    EvaluationContext(targetingKey = "user-123"),
    object : EvaluationContextCallback {
        override fun onSuccess() {
            // The first callback succeeds once under the selected policy.
            // A cache completion can happen synchronously or on the disk-loading thread.
            println("Initial flag configuration is available")
        }

        override fun onFailure(error: Throwable) {
            // Apply the application's initialization failure behavior.
            println("Flag initialization failed: ${error.message}")
        }
    }
)

// At the point where the application actually uses this flag:
val details = client.resolve("new-checkout", false)
val useNewCheckout = details.value
val reason = details.reason

// When this observer is no longer needed:
client.state.removeListener(listener)
```

The example uses the public `FlagsClient` interface; its `DatadogFlagsClient` implementation is internal. You can also set the policy for all newly created clients with `FlagsConfiguration.Builder().clientReadyPolicy(...)`. A named builder returns an existing client if one is already registered; it does not reconfigure that instance.

For successful resolutions, `CACHED` means the assignment was loaded from disk and no differing context has been requested. `STALE` means the currently requested context differs from the assignment's context; it can apply to either disk-loaded or previously fetched assignments. This mismatch overlay is Datadog's chosen precedence for the STALE reason, not the complete OpenFeature definition. Accepted network responses retain their original reasons. An empty configuration can complete initialization but cannot provide a successful flag resolution. Resolution errors retain `ERROR` and the supplied default value.

State and reason describe the current availability and evaluation. They do not record the exact historical trigger of initialization: `STALE` masks disk versus network origin, empty configurations provide no successful reason, and state and resolution reads are not an atomic pair. State `Stale` plus reason `CACHED` identifies disk-origin data without a context mismatch, but does not distinguish early disk readiness from fallback after a failed network attempt. No readiness-trigger metadata is exposed.

The initialization deadline bounds the first callback only. Late disk/network results can recover the client without completing that callback twice. A superseded context operation reports its own outcome without replacing the newer assignments or state.

The OpenFeature adapter uses its existing provider event stream. The pinned Kotlin SDK can overwrite a native `Stale` state with SDK `READY` when initialization returns normally; do not infer native freshness from OpenFeature client status. Use this native API when that distinction matters. Configuration-change notifications and per-flag observation are separate from this readiness policy.

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
