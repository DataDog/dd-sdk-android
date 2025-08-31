# Datadog Integration for Apollo-Kotlin

## Getting Started

Note that we support only Apollo version 4.

To include the integration for [Apollo-Kotlin][1] in your project, add the
following to your application's `build.gradle` file.

```groovy
dependencies {
    implementation "com.datadoghq:dd-sdk-android-okhttp:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-rum:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-apollo:<latest-version>"
}
```

### Initial Setup

1. Setup RUM monitoring, see the dedicated [Datadog Android RUM Collection documentation][2] to learn how.
2. Setup OkHttp instrumentation with Datadog RUM SDK, see the [dedicated documentation][3] to learn how.

Add the Datadog interceptor to your apollo client setup:
 
```kotlin
val apolloClient = ApolloClient.Builder()
    .serverUrl([graphQLEndpoint])
    .addInterceptor(DatadogApolloInterceptor())
    .okHttpClient([okhttpClientConfiguration])
    .build()
```

This automatically adds Datadog headers to your GraphQL requests, allowing them to be tracked
by Datadog. Note that while `query` and `mutation` type operations are tracked, `subscription` operations are not.

#### Sending GraphQL Payloads

GraphQL payload capture is disabled by default, so if you want to use it you must set the `captureGraphQLPayloads` flag in the RumConfiguration as follows: 

```kotlin
 val rumConfig = RumConfiguration.Builder(applicationId)
            .captureGraphQLPayloads(true)
            .build()
```

## Contributing

For details on contributing, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://github.com/apollographql/apollo-kotlin
[2]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=kotlin
[3]: https://docs.datadoghq.com/real_user_monitoring/android/advanced_configuration/?tab=kotlin#automatically-track-network-requests
