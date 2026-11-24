/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.net

import com.datadog.android.api.instrumentation.network.HttpCacheDelivery
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.api.instrumentation.network.cacheDelivery
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.model.ResourceEvent

// The backend only checks whether the transfer size is zero or positive, so when the size of a
// server validation exchange isn't measured (or measured as zero), a positive placeholder is
// enough to report it. A zero would make the backend report a local cache hit instead.
internal const val VALIDATED_HIT_TRANSFER_SIZE_FALLBACK: Long = 300L

// derived from the schema, so that a change of the values there can't silently break them
internal val DELIVERY_TYPE_CACHE: String = ResourceEvent.DeliveryType.CACHE.toJson().asString
internal val DELIVERY_TYPE_OTHER: String = ResourceEvent.DeliveryType.OTHER.toJson().asString

/**
 * Maps the delivery evidence exposed by a networking library to the `resource.delivery_type` and
 * `resource.transfer_size` attributes, as the iOS SDK does: `cache` for a response served from the
 * device HTTP cache, `other` for a response downloaded over the network. The backend derives
 * `resource.cache_status` and `resource.cache_reason` from them.
 *
 * It returns attributes rather than a dedicated object because attributes are the only way to
 * pass data to the resource: `AdvancedNetworkRumMonitor.stopResource` only accepts an attributes
 * map, and its signature is used by cross-platform SDKs. Cross-platform SDKs set the same
 * attributes in `RumMonitor.stopResource`, as they already do for `RumAttributes.REQUEST_HEADERS`,
 * so native and cross-platform values are validated in the same place in `RumResourceScope`.
 *
 * @return the attributes to add to the RUM resource, empty if the networking library doesn't tell
 * how the response was delivered.
 */
internal fun HttpResponseInfo.createCacheDeliveryAttributes(): Map<String, Any?> {
    return when (val delivery = cacheDelivery) {
        HttpCacheDelivery.Unknown -> emptyMap()
        // A network load always receives bytes: zero or less means not measured. null rather than
        // absent, so that a zero transfer size set by another attributes source can't make it
        // look like a local cache hit
        is HttpCacheDelivery.NetworkLoad -> deliveryAttributes(
            deliveryType = DELIVERY_TYPE_OTHER,
            transferSize = delivery.networkBytes?.takeIf { it > 0 }
        )
        HttpCacheDelivery.LocalHit -> cacheDeliveryAttributes(transferSize = 0L)
        is HttpCacheDelivery.ValidatedHit -> cacheDeliveryAttributes(
            transferSize = delivery.networkBytes?.takeIf { it > 0 } ?: VALIDATED_HIT_TRANSFER_SIZE_FALLBACK
        )
        // null rather than absent, so that a transfer size set by another attributes source
        // can't make an unknown cache reason look like a local or a validated cache hit
        HttpCacheDelivery.CachedUnknownReason -> cacheDeliveryAttributes(transferSize = null)
    }
}

private fun cacheDeliveryAttributes(transferSize: Long?): Map<String, Any?> =
    deliveryAttributes(deliveryType = DELIVERY_TYPE_CACHE, transferSize = transferSize)

private fun deliveryAttributes(deliveryType: String, transferSize: Long?): Map<String, Any?> = mapOf(
    RumAttributes.RESOURCE_DELIVERY_TYPE to deliveryType,
    RumAttributes.RESOURCE_TRANSFER_SIZE to transferSize
)
