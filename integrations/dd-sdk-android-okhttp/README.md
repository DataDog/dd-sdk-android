# Datadog SDK instrumentation for OkHttp

See the dedicated [Datadog SDK instrumentation for OkHttp documentation][1] to learn how track network requests made by `OkHttp` library automatically.

## Cache status

RUM Resources report whether OkHttp served the response from its HTTP [`Cache`][2]. The SDK sets
`resource.delivery_type` and `resource.transfer_size`, and Datadog derives `resource.cache_status`
and `resource.cache_reason` from them. No configuration is needed.

| Response | `delivery_type` | `transfer_size` |
| --- | --- | --- |
| Served from the cache, no network request | `cache` | `0` |
| Served from the cache after a `304 Not Modified` validation | `cache` | `300` (OkHttp does not expose the validation byte count) |
| Served from the cache after a redirect that went over the network | `cache` | not set (the redirect transferred bytes, so this is not a local cache hit) |
| Downloaded from the network | not set | not set |

The SDK reads this information from `Response.cacheResponse` and `Response.networkResponse`. These are
only meaningful for an application interceptor. Register `DatadogInterceptor` with `addInterceptor()`,
not `addNetworkInterceptor()`: a network interceptor never sees responses served from the cache.
`configureDatadogInstrumentation()` registers its RUM interceptor correctly.

[1]: https://docs.datadoghq.com/real_user_monitoring/android/advanced_configuration/?tab=kotlin#automatically-track-network-requests
[2]: https://square.github.io/okhttp/features/caching/
