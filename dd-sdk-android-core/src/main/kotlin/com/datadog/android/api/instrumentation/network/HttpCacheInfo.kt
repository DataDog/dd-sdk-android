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
 * HTTP cache or downloaded over the network.
 *
 * It is implemented by the [HttpResponseInfo] instances of the networking libraries that expose
 * this information. Rather than checking for this interface, read the
 * [HttpResponseInfo.cacheDelivery] extension, which returns [HttpCacheDelivery.Unknown] when the
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
     * The networking library does not tell how the payload was delivered.
     */
    data object Unknown : HttpCacheDelivery

    /**
     * The payload was downloaded over the network, without using the device HTTP cache.
     *
     * @param networkBytes the number of bytes (headers and body) received over the network for the
     * request, or null if the networking library does not measure it.
     */
    class NetworkLoad(val networkBytes: Long?) : HttpCacheDelivery {
        // not a data class, so that copy and componentN stay out of the public ABI

        override fun equals(other: Any?): Boolean = other is NetworkLoad && other.networkBytes == networkBytes

        override fun hashCode(): Int = networkBytes.hashCode()

        override fun toString(): String = "NetworkLoad(networkBytes=$networkBytes)"
    }

    /**
     * The payload was served from the device HTTP cache, without validation by the server. A
     * redirect leading to the cached entry may still have gone over the network.
     */
    data object LocalHit : HttpCacheDelivery

    /**
     * The payload was served from the device HTTP cache, after the server confirmed that the cached
     * entry is still valid (e.g. with a `304 Not Modified` response).
     *
     * @param networkBytes the number of bytes received over the network for the validation
     * exchange, or null if the networking library does not measure it.
     */
    class ValidatedHit(val networkBytes: Long?) : HttpCacheDelivery {
        // not a data class, so that copy and componentN stay out of the public ABI

        override fun equals(other: Any?): Boolean = other is ValidatedHit && other.networkBytes == networkBytes

        override fun hashCode(): Int = networkBytes.hashCode()

        override fun toString(): String = "ValidatedHit(networkBytes=$networkBytes)"
    }

    /**
     * The payload was served from the device HTTP cache, but the networking library doesn't tell
     * whether the server validated it.
     */
    data object CachedUnknownReason : HttpCacheDelivery
}

/**
 * For internal usage only.
 *
 * How the response payload was delivered with regard to the device HTTP cache. Returns
 * [HttpCacheDelivery.Unknown] when the networking library does not expose this information.
 */
@InternalApi
val HttpResponseInfo.cacheDelivery: HttpCacheDelivery
    get() = (this as? HttpCacheInfo)?.cacheDelivery ?: HttpCacheDelivery.Unknown
