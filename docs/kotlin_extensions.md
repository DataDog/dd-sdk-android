# Kotlin Extensions

Take advantage of the Kotlin syntax to improve your usage of the dd-sdk-android library.

**Note**: This package is an extension of the main package, so add both dependencies into your gradle file.

## Setup
```conf
dependencies {
    implementation "com.datadoghq:dd-sdk-android:x.x.x"
    implementation "com.datadoghq:dd-sdk-android-ktx:x.x.x"
}
```

### Extensions

1. Span extension methods:

```kotlin

// Attaches the throwable as a special attribute to the Span and in the same time sends also a Log message
// with all the needed information.
span.setError(throwable)

// Attaches the error message as a special attribute to the Span and in the same time sends also a Log message
// with all the needed information.
span.setError(message)

```

```kotlin

// Automatically creates a span around the provided lambda
withinSpan(spanName, parentSpan){
  // Your code here
}

```

2. Closeable extension methods:

```kotlin

// Executes the given [block] function on the [Closeable] instance, intercepts any Exception and sends it in 
// a RUM error event closing the [Closeable] instance afterwards.

val closeable:Closeable
closeable.useMonitored { 
    // Your code here
}

```

3. Extensions for OkHttp request builder:

```kotlin

  // If you are using the Datadog TracingInterceptor for OkHttp request, this adds the provided parentSpan as the parent
  // of the span created around the request.
  val okHttpRequest = Request.Builder().parentSpan(span).build()

```
