# Datadog Integration for RxJava

## Getting Started 

To include the Datadog integration for [RxJava][1] in your project, simply add the
following to your application's `build.gradle` file.

```
dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-rx:<latest-version>"
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

Following RxJava's [Generated API documentation][2], you just have to apply the `doOnError` operator on your `Observable`,
`Flowable`, `Single`, `Maybe` or `Completable` and pass an instance of `DatadogErrorConsumer`.

Doing so will automatically intercept any Exception thrown in the upper stream by creating RUM Error events.

**Note** : If you are using `Kotlin` in your codebase you could also use the extension: `sendErrorToDatadog()`.

Java: 

```java
    Observable.create<T>{...}
    .doOnError(new DatadogErrorConsumer())
    ...
```

Kotlin: 

```java
    Observable.create<T>{...}
    .publishErrorsToRum()
    ...
```

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://github.com/ReactiveX/RxJava
[2]: https://github.com/ReactiveX/RxJava/wiki