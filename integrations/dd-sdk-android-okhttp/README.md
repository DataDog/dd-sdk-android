# Datadog SDK instrumentation for OkHttp

See the dedicated [Datadog SDK instrumentation for OkHttp documentation][1] to learn how track network requests made by `OkHttp` library automatically.

## Cache status

RUM Resources report how OkHttp delivered the response: from its HTTP [`Cache`][2] or over the
network. The SDK sets `resource.delivery_type` and `resource.transfer_size`, and Datadog derives
`resource.cache_status` and `resource.cache_reason` from them. No configuration is needed.

| Response | `delivery_type` | `transfer_size` |
| --- | --- | --- |
| Served from the cache, no network request | `cache` | `0` |
| Served from the cache after a `304 Not Modified` validation | `cache` | `300` (placeholder: OkHttp does not expose the validation byte count) |
| Served from the cache after a redirect that went over the network | `cache` | `0` (the redirect does not validate the cached entry) |
| Downloaded from the network | `other` | not set (OkHttp does not expose the received header byte count) |

The SDK reads this information from `Response.cacheResponse` and `Response.networkResponse`,
which only an application interceptor sees. Register
`DatadogInterceptor` with `addInterceptor()`, not `addNetworkInterceptor()`. OkHttp runs its cache
before the network interceptors, so a network interceptor is not called for a response served from
the cache. For a `304` validation, a network interceptor sees the response without its cache
context. In both cases, no cache status is reported, and the SDK logs a warning.
`configureDatadogInstrumentation()` registers its RUM interceptor correctly.

[1]: https://docs.datadoghq.com/real_user_monitoring/android/advanced_configuration/?tab=kotlin#automatically-track-network-requests
[2]: https://square.github.io/okhttp/features/caching/
