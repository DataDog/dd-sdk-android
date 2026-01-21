# Datadog Integration for Android TV applications

## Getting Started 

To include the Datadog integration for Android TV in your project, add the following to your application's `build.gradle.kts` file.

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-rum:<latest-version>")
    implementation("com.datadoghq:dd-sdk-android-tv:<latest-version>")
}
```

### Initial Setup

1. Setup RUM monitoring, see the dedicated [Datadog Android RUM Collection documentation][1] to learn how.
2. To receive more information about RUM action events for Android TV applications, provide the `LeanbackViewAttributesProvider` when configuring RUM.

#### Kotlin Example

```kotlin
    val rumConfiguration = RumConfiguration.Builder(applicationId)
        ...
        .trackInteractions(touchTargetExtraAttributesProviders = arrayOf(LeanbackViewAttributesProvider()))
        .build()
```

#### Java Example

```java
    RumConfiguration rumConfiguration = new RumConfiguration.Builder(applicationId)
        ...
        .trackInteractions(new ViewAttributesProvider[]{new LeanbackViewAttributesProvider()})
        .build();
```

## Contributing

Pull requests are welcome. Open an issue first to discuss what you would like to change. For more information, see our [Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=kotlin
