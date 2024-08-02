# Datadog Integration for SQLDelight

## Getting Started 

To include the Datadog integration for [SQLDelight][1] in your project, simply add the
following to your application's `build.gradle` file.

```groovy
dependencies {
    implementation "com.datadoghq:dd-sdk-android-rum:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-trace:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-sqldelight:<latest-version>"
}
```

### Initial Setup

1. Setup RUM monitoring, see the dedicated [Datadog Android RUM Collection documentation][2] to learn how.
2. Setup Trace monitoring, see the dedicated [Datadog Android Trace Collection documentation][3] to learn how.
3. Following SQLDelight's [Generated API documentation][1], you just have to provide the `DatadogSqliteCallback` in the 
`AndroidSqliteDriver` constructor.

Doing this detects whenever a database is corrupted and sends a relevant
RUM error event for it.

```kotlin
    val database = YourDatabase(
                AndroidSqliteDriver(
                    YourDatabase.Schema,
                    context,
                    callback = DatadogSqliteCallback(YourDatabase.Schema)
                ))
```

### Extension methods for traced transactions

If you are using SQLDelight transactions, you can trace the transaction block using the following 2 methods:

```kotlin
    database.yourQuery.transactionTraced("<SPAN_NAME>", noEnclosing) {    
        // …
    }
```

```kotlin
    val result = database.yourQuery.transactionTracedWithResult("<SPAN_NAME>", noEnclosing) {    
        // …
    }
```

They behave as the default methods (`transaction(noEnclosing,block)`, `transactionWithResult(noEnclosing,block)`) and they simply require a span name as an 
extra argument.

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://cashapp.github.io/sqldelight/android_sqlite/
[2]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=kotlin
[3]: https://docs.datadoghq.com/tracing/trace_collection/dd_libraries/android/?tab=kotlin
