# Datadog Integration for Apollo-Kotlin

## Getting started

**Note:** The integration only supports Apollo version 4.

To include the integration for [Apollo-Kotlin][1] in your project, add the
following to your application's `build.gradle` file.

```groovy
dependencies {
    implementation "com.datadoghq:dd-sdk-android-okhttp:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-rum:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-apollo:<latest-version>"
}
```

### Initial setup

1. Set up RUM monitoring with [Datadog Android RUM][2].
2. Set up OkHttp instrumentation with the [Datadog RUM SDK][3].

Add the Datadog interceptor to your Apollo Client setup:
 
```kotlin
val apolloClient = ApolloClient.Builder()
    .serverUrl([graphQLEndpoint])
    .addInterceptor(DatadogApolloInterceptor())
    .okHttpClient([okhttpClientConfiguration])
    .build()
```

This automatically adds Datadog headers to your GraphQL requests, allowing them to be tracked
by Datadog. Note that while `query` and `mutation` type operations are tracked, `subscription` operations are not.

#### Sending GraphQL payloads

GraphQL payload sending is disabled by default. To enable it, set the `sendGraphQLPayloads` flag in the DatadogApolloInterceptor constructor as follows:

```kotlin
DatadogApolloInterceptor(sendGraphQLPayloads = true)
```

## Contributing

For details on contributing, read the 
[Contributing Guide](../../CONTRIBUTING.md).

## License

[Apache License, v2.0](../../LICENSE)

[1]: https://github.com/apollographql/apollo-kotlin
[2]: https://docs.datadoghq.com/real_user_monitoring/android/?tab=kotlin
[3]: https://docs.datadoghq.com/real_user_monitoring/android/advanced_configuration/?tab=kotlin#automatically-track-network-requests
