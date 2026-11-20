/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.api.instrumentation.network

import com.datadog.android.lint.InternalApi

/**
 * For internal usage only.
 *
 * This interface indicates that a response exposes whether its payload was served from the device
 * HTTP cache.
 *
 * It is implemented by the [HttpResponseInfo] instances of the networking libraries that expose
 * this information. Rather than checking for this interface, read the
 * [HttpResponseInfo.cacheDelivery] extension, which returns [HttpCacheDelivery.NotCached] when the
 * underlying library does not support it.
 */
@InternalApi
interface HttpCacheInfo {

    /**
     * How the response payload was delivered with regard to the device HTTP cache.
     */
    val cacheDelivery: HttpCacheDelivery
}

/**
 * For internal usage only.
 *
 * Describes how a response payload was delivered with regard to the device HTTP cache.
 */
@InternalApi
sealed interface HttpCacheDelivery {

    /**
     * The payload was not served from the device HTTP cache, or the networking library does not
     * expose this information.
     */
    data object NotCached : HttpCacheDelivery

    /**
     * The payload was served from the device HTTP cache, without any network exchange.
     */
    data object LocalHit : HttpCacheDelivery

    /**
     * The payload was served from the device HTTP cache, after the server confirmed that the cached
     * entry is still valid (e.g. with a `304 Not Modified` response).
     *
     * @param networkBytes the number of bytes received over the network for the validation
     * exchange, or null if the networking library does not measure it.
     */
    data class ValidatedHit(val networkBytes: Long?) : HttpCacheDelivery

    /**
     * The payload was served from the device HTTP cache, but the networking library doesn't tell
     * whether a network exchange happened.
     */
    data object CachedUnknownReason : HttpCacheDelivery
}

/**
 * For internal usage only.
 *
 * How the response payload was delivered with regard to the device HTTP cache. Returns
 * [HttpCacheDelivery.NotCached] when the networking library does not expose this information.
 */
@InternalApi
val HttpResponseInfo.cacheDelivery: HttpCacheDelivery
    get() = (this as? HttpCacheInfo)?.cacheDelivery ?: HttpCacheDelivery.NotCached
