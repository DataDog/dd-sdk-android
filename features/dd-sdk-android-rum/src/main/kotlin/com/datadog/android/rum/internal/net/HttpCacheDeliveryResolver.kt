/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.net

import com.datadog.android.api.instrumentation.network.HttpCacheDelivery
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.api.instrumentation.network.cacheDelivery
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.RumAttributes

/**
 * For internal usage only.
 *
 * Maps the cache evidence exposed by a networking library to the `resource.delivery_type` and
 * `resource.transfer_size` attributes. The backend derives `resource.cache_status` and
 * `resource.cache_reason` from them.
 */
@InternalApi
class HttpCacheDeliveryResolver {

    /**
     * Resolves the RUM resource attributes describing how the response was delivered with regard
     * to the device HTTP cache.
     *
     * @param responseInfo the response information
     * @return the attributes to add to the RUM resource, empty if the response wasn't served from
     * the cache or the networking library doesn't tell.
     */
    fun resolve(responseInfo: HttpResponseInfo): Map<String, Any?> {
        return when (val delivery = responseInfo.cacheDelivery) {
            HttpCacheDelivery.NotCached -> emptyMap()
            HttpCacheDelivery.LocalHit -> mapOf(
                RumAttributes.INTERNAL_RESOURCE_DELIVERY_TYPE to DELIVERY_TYPE_CACHE,
                RumAttributes.INTERNAL_RESOURCE_TRANSFER_SIZE to 0L
            )
            is HttpCacheDelivery.ValidatedHit -> mapOf(
                RumAttributes.INTERNAL_RESOURCE_DELIVERY_TYPE to DELIVERY_TYPE_CACHE,
                RumAttributes.INTERNAL_RESOURCE_TRANSFER_SIZE to
                    (delivery.networkBytes?.takeIf { it > 0 } ?: VALIDATED_HIT_TRANSFER_SIZE_FALLBACK)
            )
            // null rather than absent, so that a transfer size set by another attributes source
            // can't make an unknown cache reason look like a local or a validated cache hit
            HttpCacheDelivery.CachedUnknownReason -> mapOf(
                RumAttributes.INTERNAL_RESOURCE_DELIVERY_TYPE to DELIVERY_TYPE_CACHE,
                RumAttributes.INTERNAL_RESOURCE_TRANSFER_SIZE to null
            )
        }
    }

    internal companion object {
        // The backend only checks whether the transfer size is zero or positive, so when the size
        // of a server validation exchange isn't measured (or measured as zero), a positive
        // placeholder is enough to report it. A zero would make the backend report a local cache
        // hit instead.
        internal const val VALIDATED_HIT_TRANSFER_SIZE_FALLBACK: Long = 300L

        internal const val DELIVERY_TYPE_CACHE: String = "cache"
    }
}
