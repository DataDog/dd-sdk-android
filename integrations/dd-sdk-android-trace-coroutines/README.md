# Datadog Trace extensions for Kotlin Coroutines

## Getting started

To include the Datadog Trace extensions for Kotlin Coroutines in your project, simply add the
following to your application's `build.gradle` file:

```groovy
dependencies {
    implementation "com.datadoghq:dd-sdk-android-trace:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-trace-coroutines:<latest-version>"
}
```

### Extensions

#### Tracing Coroutine code

If you're using coroutines, you can trace the coroutine blocks using one of the following method. They behave like the usual coroutine methods, and simply require a span operation name.

```kotlin
    fun doSomething() {
        GlobalScope.launchTraced("<SPAN_NAME>", Dispatchers.IO) {
            // …
        }

        runBlockingTraced("<SPAN_NAME>", Dispatchers.IO) {
            // …
        }
    }

    suspend fun coroutineMethod() {
        val deferred = asyncTraced("<SPAN_NAME>", Dispatchers.IO) {
            // …
        }

        withContextTraced("<SPAN_NAME>", Dispatchers.Main) {
            // …
        }

        val result = deferred.awaitTraced("<SPAN_NAME>")
    }
```

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)
