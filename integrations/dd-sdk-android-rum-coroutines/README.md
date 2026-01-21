# Datadog RUM extensions for Kotlin Coroutines

## Getting started

To include the Datadog RUM extensions for Kotlin Coroutines in your project, simply add the
following to your application's `build.gradle.kts` file.

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-rum:<latest-version>")
    implementation("com.datadoghq:dd-sdk-android-rum-coroutines:<latest-version>")
}
```

### Extensions

#### Reporting Coroutine Flow errors

If you're using Kotlin Coroutine Flow, you can propagate Flow errors to your RUM dashboard using the `sendErrorToDatadog()` method.

```kotlin
    suspend fun coroutineMethod() {
        val flow = flow { emit(/*…*/) }

        flow.sendErrorToDatadog().collect {
            // …
        }
    }
```

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)
