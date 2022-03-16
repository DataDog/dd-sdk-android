# Datadog Integration for Android TV applications

## Getting Started 

To include the Datadog integration for AndroidTv in your project, simply add the
following to your application's `build.gradle` file.

```groovy
dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-tv:<latest-version>"
}
```

### Initial Setup

Before you can use the SDK, you need to setup the library with your application
context, your Client token and your Application ID. 
To generate a Client token and an Application ID please check **UX Monitoring > RUM Applications > New Application**
in the Datadog dashboard.

To get more information in the RUM Action events for AndroidTV application you can provide the [AndroidTvViewAttributesProvider][1]
when initializing the SDK.

```kotlin
    class SampleApplication : Application() {
        override fun onCreate() {
            super.onCreate()
            val configuration = Configuration.Builder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    crashReportsEnabled = true,
                    rumEnabled = true
                )
                .useSite(DatadogSite.US3)
                .trackInteractions(touchTargetExtraAttributesProviders = arrayOf(AndroidTvViewAttributesProvider()))
                .trackLongTasks(durationThreshold)
                .useViewTrackingStrategy(strategy)
                .build()
            val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
            Datadog.initialize(this, credentials, configuration, trackingConsent)
        }
    }
```

```java
public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Configuration configuration =
                new Configuration.Builder(true, true, true, true)
                        .trackInteractions(new ViewAttributesProvider[]{new AndroidTvViewAttributesProvider()})
                        .trackLongTasks(durationThreshold)
                        .useViewTrackingStrategy(strategy)
                        .build();
        final Credentials credentials = new Credentials( < CLIENT_TOKEN >, <ENV_NAME >, <
        APP_VARIANT_NAME >, <APPLICATION_ID >);
        Datadog.initialize(this, credentials, configuration, trackingConsent);
    }
}
```



## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../CONTRIBUTING.md).

## License

[Apache License, v2.0](../LICENSE)

[1]: https://github.com/DataDog/dd-sdk-android/dd-sdk-android-tv/src/main/com/datadog/android/tv/AndroidTvViewAttributesProvider.kt
