# Datadog OpenFeature Provider for Android

The Datadog OpenFeature Provider for Android allows you to use the [OpenFeature](https://openfeature.dev/) standard API with Datadog's Feature Flagging and Experimentation platform.

## What is OpenFeature?

OpenFeature is a vendor-neutral, community-driven specification and SDK for feature flagging. It provides a unified API for feature flag evaluation that works across different providers, making it easy to switch vendors or integrate multiple feature flag systems.

## Getting started

### Prerequisites

Before using the OpenFeature provider, you must:
1. Set up the core Datadog SDK (see [Datadog Android SDK setup documentation][1])
2. Enable the Datadog Feature Flags feature (see [dd-sdk-android-flags README](../dd-sdk-android-flags/README.md))

### Add dependencies

Add both the Datadog Feature Flags SDK and OpenFeature Provider to your application's `build.gradle` file:

```groovy
dependencies {
    implementation "com.datadoghq:dd-sdk-android-flags:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-flags-openfeature:<latest-version>"

    // Recommended: RUM integration to corelate flags and RUM session data
    implementation "com.datadoghq:dd-sdk-android-rum:<latest-version>"
}
```

### Initial setup

1. Initialize the Datadog SDK and enable the Feature Flags feature:

```kotlin
// Initialize the core Datadog SDK
val coreConfiguration = Configuration.Builder(
    clientToken = "<YOUR_CLIENT_TOKEN>",
    env = "<YOUR_ENVIRONMENT>",
    variant = "<YOUR_APP_VARIANT>"
).build()

Datadog.initialize(this, coreConfiguration, trackingConsent)

// Enable the Feature Flags feature
val flagsConfig = FlagsConfiguration.Builder().build()
Flags.enable(flagsConfig)
```

2. (Recommended) Enable RUM for enriched feature flag analytics:

```kotlin
val rumConfig = RumConfiguration.Builder(applicationId = "<YOUR_RUM_APPLICATION_ID>")
    .build()

Rum.enable(rumConfig)
```

> **Note:** RUM integration is automatically enabled in the Feature Flags module. See [Disable RUM integration](#disable-rum-integration).


## Setup

### Create and configure the OpenFeature provider

Create a `FlagsClient` and convert it to an OpenFeature provider using the `asOpenFeatureProvider()` extension function:

```kotlin
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.openfeature.asOpenFeatureProvider
import dev.openfeature.kotlin.sdk.OpenFeatureAPI

// Create a FlagsClient and convert to OpenFeature provider
val provider = FlagsClient.Builder().build().asOpenFeatureProvider()

// Set it as the OpenFeature provider
OpenFeatureAPI.setProviderAndWait(provider)
```

### Configuration options

You can configure the underlying `FlagsClient` before converting it to an OpenFeature provider:

#### Custom endpoints

```kotlin
val provider = FlagsClient.Builder()
    .useCustomFlagEndpoint("https://your-proxy.example.com/flags")
    .useCustomExposureEndpoint("https://your-proxy.example.com/exposure")
    .build()
    .asOpenFeatureProvider()
```

#### Disable RUM integration

```kotlin
val flagsConfig = FlagsConfiguration.Builder()
    .rumIntegrationEnabled(false)
    .build()
Flags.enable(flagsConfig)

val provider = FlagsClient.Builder().build().asOpenFeatureProvider()
```

## Using the OpenFeature API

Once the provider is configured, you can use the standard OpenFeature Kotlin SDK API to evaluate feature flags:

```kotlin
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value

// Set evaluation context
OpenFeatureAPI.setEvaluationContext(
    ImmutableContext(
        targetingKey = "user-123",
        attributes = mapOf(
            "email" to Value.String("user@example.com"),
            "plan" to Value.String("premium")
        )
    )
)

// Get a client and evaluate flags
val client = OpenFeatureAPI.getClient()
val isEnabled = client.getBooleanValue("my-feature", false)
```

**Important notes:**
- The targeting key must be consistent for the same user or entity to ensure consistent flag evaluation across requests.
- For anonymous users, use a **persistent UUID** as the targeting key (store it in `SharedPreferences`).
- All attribute values must be strings (use `Value.String()`).

For complete details on using the OpenFeature API, including flag evaluation methods, hooks, state management, and events, see the [OpenFeature Kotlin SDK documentation](https://openfeature.dev/docs/reference/technologies/client/kotlin/)

## Integration with RUM

When RUM is enabled in your application and RUM integration is enabled in the Flags configuration (default), flag evaluations are automatically:
- Attached to the current RUM view
- Visible in the Datadog RUM dashboard
- Associated with user sessions for analysis

This allows you to correlate feature flag usage with application performance, errors, and user behavior.

### Prerequisites for RUM integration

1. Add the `dd-sdk-android-rum` dependency to your project
2. Enable RUM before initializing the Flags feature (see [Initial setup](#initial-setup) section)
3. Ensure `rumIntegrationEnabled` is set to `true` in your `FlagsConfiguration` (this is the default)

If RUM is not enabled, the OpenFeature provider will continue to work normally, but flag evaluations will not appear in RUM views.

## Best practices

- **Consistent targeting keys**: Use consistent targeting keys (user ID) to ensure users see consistent feature flag values across sessions.
- **Provide meaningful defaults**: Always provide sensible default values that maintain core functionality if flag evaluation fails.
- **Set context early**: Set the evaluation context as early as possible in your application lifecycle, typically after user authentication.
- **All attributes as strings**: All attribute values must be strings (use `Value.String()`).

## Differences from direct FlagsClient usage

The OpenFeature provider provides a standardized API but has some differences from using `FlagsClient` directly:

| Feature               | **FlagsClient**                       | **OpenFeature Provider**                  |
|-----------------------|---------------------------------------|-------------------------------------------|
| **API Standard**      | Datadog-specific                      | OpenFeature standard                      |
| **Evaluation Context**| Per client instance                   | Global/static context                     |
| **Structured Flags**  | Returns `JSONObject`                  | Returns `Value.Structure`                 |
| **Vendor Neutrality** | Datadog-specific                      | Vendor-neutral (easy to swap providers)   |
| **Type Safety**       | Kotlin-native types                   | OpenFeature `Value` types                 |
| **State Management**  | Manual listener registration          | Flow-based observation                    |


Choose the OpenFeature provider if:
- You want a vendor-neutral API
- You're already using OpenFeature in other parts of your application
- You want standardized hook support
- You prefer the static-context paradigm

Use `FlagsClient` directly if:
- You want the most direct integration with Datadog
- You prefer instance-based evaluation contexts
- You want to work with native Kotlin types directly
- You need fine-grained control over multiple independent contexts

## Further reading

- [OpenFeature Kotlin SDK documentation](https://openfeature.dev/docs/reference/technologies/client/kotlin/) - Complete API reference and usage guide
- [OpenFeature Specification](https://openfeature.dev/specification/) - OpenFeature standard specification
- [Datadog Feature Flags documentation][2] - Datadog Feature Flags platform documentation
- [Datadog Android SDK setup documentation][1] - Initial setup guide

[1]: https://docs.datadoghq.com/real_user_monitoring/application_monitoring/android/setup
[2]: https://docs.datadoghq.com/getting_started/feature_flags/
