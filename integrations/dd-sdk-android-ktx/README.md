# Datadog extensions for Kotlin

## Getting Started 

To include the Datadog extensions for Kotlin in your project, simply add the
following to your application's `build.gradle` file.

```groovy
dependencies {
    implementation "com.datadoghq:dd-sdk-android-ktx:<latest-version>"
}
```

### Extension

#### Running a lambda within a Span

To monitor the performance of a given lambda, you can use the `withinSpan()` method. By default, a scope will be created for the span, but you can disable this behavior by setting the `activate` parameter to false.

```kotlin
    withinSpan("<SPAN_NAME>", parentSpan, activate) {
        // Your code here
    }
```

#### Span extension methods

You can mark a span as having an error using one of the following `error()` methods.

```kotlin
    val span = tracer.buildSpan("<SPAN_NAME>").start()
    try {
        // …
    } catch (e: IOException) {
        span.setError(e)
    }
    span.finish()
```

```kotlin
    val span = tracer.buildSpan("<SPAN_NAME>").start()
    if (invalidState) {
        span.setError("Something unexpected happened")
    }
    span.finish()
```

#### `Closeable` extension methods:

```kotlin

// Executes the given [block] function on the [Closeable] instance, intercepts any Exception and sends it in 
// a RUM error event closing the [Closeable] instance afterwards.

val closeable: Closeable = ...
closeable.useMonitored { 
    // Your code here
}

```

#### OkHttp Request extension method

If you are using the `DatadogInterceptor` to trace your OkHttp requests, you can add a parent span using the `parentSpan()` method.

```kotlin
  val request = Request.Builder()
            .parentSpan(span)
            .build()
```

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

#### Tracing SQLite transaction

If you are using SQLiteDatabase to persist data locally, you can trace the database transaction using the following method:

```kotlin
   sqliteDatabase.transactionTraced("<SPAN_NAME>",isExclusive) { database ->
        // Your queries here
        database.insert("<TABLE_NAME>", null, contentValues)

        // Decorate the Span
        setTag("<TAG_KEY>", "<TAG_VALUE>")
   } 
```
It behaves like the `SQLiteDatabase.transaction` method provided in the `core-ktx` AndroidX package and only requires a span operation name.

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)
