# Datadog Feature Flags SDK for Android

The Datadog Feature Flags SDK for Android allows you to evaluate feature flags and experiments in your Android application and automatically send flag evaluation data to Datadog for monitoring and analysis.

## Getting started

Add the Datadog Feature Flags SDK to your application's `build.gradle` file:

```groovy
dependencies {
    implementation "com.datadoghq:dd-sdk-android-flags:<latest-version>"
    
    // Recommended: RUM integration drives analysis and enriches RUM session data
    implementation "com.datadoghq:dd-sdk-android-rum:<latest-version>"
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

**Note:** This setting only has an effect if you have enabled RUM (see [Initial Setup](#initial-setup) section). If RUM is not enabled, flag evaluations are not sent to RUM regardless of this setting.

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

##### Error Codes
- `FLAG_NOT_FOUND` - The flag could not be found
- `PARSE_ERROR` - Error parsing the flag value
- `TYPE_MISMATCH` - The flag type doesn't match the expected type
- `TARGETING_KEY_MISSING` - No targeting key was provided
- `INVALID_CONTEXT` - The evaluation context is invalid
- `PROVIDER_NOT_READY` - The provider is not yet ready
- `PROVIDER_FATAL` - The provider encountered a fatal error
- `GENERAL` - A general error occurred

### Retrieving existing clients

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

## Prerequisites for RUM Integration
1. Add the `dd-sdk-android-rum` dependency to your project
2. Enable RUM before initializing the Flags feature (see [Initial Setup](#initial-setup) section)
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
