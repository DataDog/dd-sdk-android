# Datadog Trace SDK for Android

See the dedicated [Datadog Android Trace Collection documentation][1] to learn how to send traces from your Android or Android TV application to Datadog.

[1]: https://docs.datadoghq.com/tracing/trace_collection/dd_libraries/android/?tab=kotlin

## User anonymous ID

When `UserInfo.anonymousId` is set, the SDK adds it to spans as `usr.anonymous_id`.
When tracking consent is `TrackingConsent.GRANTED`, traced network requests also send it
in the `baggage` header as `user.anonymous_id`, next to `user.id`.
With any other consent value, the header does not include it.
