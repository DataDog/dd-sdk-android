# Datadog Integration for SQLDelight

## Getting Started 

To include the Datadog integration for [SQLDelight][1] in your project, simply add the
following to your application's `build.gradle` file.

```
dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-sqldelight:<latest-version>"
}
```

### Initial Setup

Before you can use the SDK, you need to setup the library with your application
context, your Client token and your Application ID.
To generate a Client token and an Application ID please check **UX Monitoring > RUM Applications > New Application**
in the Datadog dashboard.

```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val configuration = Configuration.Builder(
            rumEnabled = true,
            ...
        )
                        .trackInteractions()
                        .useViewTrackingStrategy(strategy)
                        ...
                        .build()
          val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
          Datadog.initialize(this, credentials, configuration, trackingConsent)

          val monitor = RumMonitor.Builder().build()
          GlobalRum.registerIfAbsent(monitor)
   }
}
```

Following SQLDelight's [Generated API documentation][1], you just have to provide the `DatadogSqliteCallback` in the 
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
    database.yourQuery.transactionTraced("<SPAN_NAME>", noEnclosing){    
        // …
    }
```

```kotlin
    val result = database.yourQuery.transactionTracedWithResult("<SPAN_NAME>", noEnclosing){    
        // …
    }
```

They behave as the default methods (`transaction(noEnclosing,block)`, `transactionWithResult(noEnclosing,block`) and they simply require a span name as an 
extra argument.

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://cashapp.github.io/sqldelight/android_sqlite/
