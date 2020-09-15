# Datadog Integration for SQLite

## Getting Started 

To include the Datadog integration for SQLite in your project, simply add the
following to your application's `build.gradle` file.

```
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-sqlite:<latest-version>"
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
        val config = DatadogConfig.Builder("<CLIENT_TOKEN>", "<ENVIRONMENT_NAME>", "<APPLICATION_ID>").build()
        Datadog.initialize(this, config)

        val  logger = Logger.Builder()
                .setNetworkInfoEnabled(true)
                .setLogcatLogsEnabled(true)
                .setDatadogLogsEnabled(true)
                .build();
    }
}
```

Following SQLiteOpenHelper's [Generated API documentation][1], you only have to provide our implementation of the
DatabaseErrorHandler -> `DatadogDatabaseErrorHandler` in the constructor.

Doing so we will be able to detect whenever a database corruption was detected and send a relevant
RUM error event for it.

```kotlint
   class <YourOwnSqliteOpenHelper>: SqliteOpenHelper(<Context>, 
                                                     <DATABASE_NAME>, 
                                                     <CursorFactory>, 
                                                     <DATABASE_VERSION>, 
                                                     DatadogDatabaseErrorHandler()) {
     ...
   
   }
```

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../CONTRIBUTING.md).

## License

[Apache License, v2.0](../LICENSE)

[1]: https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper
