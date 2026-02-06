# Datadog Integration for RxJava

## Getting Started 

To include the Datadog integration for [RxJava][1] in your project, simply add the
following to your application's `build.gradle.kts` file.

```kotlin
dependencies {
    implementation("com.datadoghq:dd-sdk-android-rum:<latest-version>")
    implementation("com.datadoghq:dd-sdk-android-rx:<latest-version>")
}
```

### Initial Setup

1. Setup RUM monitoring, see the dedicated [Datadog Android RUM Collection documentation][2] to learn how.
2. Following RxJava's [Generated API documentation][3], you just have to apply the `doOnError` operator on your `Observable`,
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

```kotlin
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
[2]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=kotlin
[3]: https://github.com/ReactiveX/RxJava/wiki