# Datadog Integration for Android TV applications

## Getting Started 

To include the Datadog integration for Android TV in your project, add the following to your application's `build.gradle` file.

```groovy
dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-tv:<latest-version>"
}
```

### Initial Setup

Before using the SDK, you need to setup the library with your application context, client token, and application ID.
 
To generate a client token and an application ID, navigate to **UX Monitoring** > **RUM Applications** and click [**New Application**][2].

To receive more information about RUM action events for Android TV applications, provide the [`LeanbackViewAttributesProvider`][1] when initializing the SDK.

#### Kotlin Example

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
                .useSite(DatadogSite.US1)
                .trackInteractions(touchTargetExtraAttributesProviders = arrayOf(LeanbackViewAttributesProvider()))
                .trackLongTasks(durationThreshold)
                .useViewTrackingStrategy(strategy)
                .build()
            val credentials = Credentials(<CLIENT_TOKEN>, <ENV_NAME>, <APP_VARIANT_NAME>, <APPLICATION_ID>)
            Datadog.initialize(this, credentials, configuration, trackingConsent)
        }
    }
```

#### Java Example

```java
public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Configuration configuration =
                new Configuration.Builder(true, true, true, true)
                        .trackInteractions(new ViewAttributesProvider[]{new LeanbackViewAttributesProvider()})
                        .trackLongTasks(durationThreshold)
                        .useViewTrackingStrategy(strategy)
                        .useSite(DatadogSite.US1)
                        .build();
        final Credentials credentials = new Credentials( < CLIENT_TOKEN >, <ENV_NAME >, <
        APP_VARIANT_NAME >, <APPLICATION_ID >);
        Datadog.initialize(this, credentials, configuration, trackingConsent);
    }
}
```

## Contributing

Pull requests are welcome. Open an issue first to discuss what you would like to change. For more information, see our [Contributing Guide][4].

## License

[Apache License, v2.0][5]

[1]: https://github.com/DataDog/dd-sdk-android/blob/master/dd-sdk-android-tv/src/main/kotlin/com/datadog/android/tv/LeanbackViewAttributesProvider.kt
[2]: https://app.datadoghq.com/rum/application/create
[3]: https://github.com/DataDog/dd-sdk-android/blob/master/dd-sdk-android-tv/README.md
[4]: https://github.com/DataDog/dd-sdk-android/blob/master/CONTRIBUTING.md
[5]: https://github.com/DataDog/dd-sdk-android/blob/master/LICENSE
