/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.fresco

import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.v2.api.SdkCore
import com.facebook.cache.common.CacheEvent
import com.facebook.cache.common.CacheEventListener
import com.facebook.cache.common.CacheKey

/**
 * Provides an implementation of [CacheEventListener] already set up to send relevant information
 * to Datadog.
 *
 * It will automatically send RUM Error events whenever a read or write cache operation throws an exception.
 *
 * @param sdkCore SDK instance to use for reporting. If not provided, default instance will be used.
 */

class DatadogFrescoCacheListener @JvmOverloads constructor(
    private val sdkCore: SdkCore = Datadog.getInstance()
) : CacheEventListener {

    // region CacheEventListener

    /** @inheritDoc */
    override fun onMiss(cacheEvent: CacheEvent) {
        // NoOp
    }

    /** @inheritdoc */
    override fun onReadException(cacheEvent: CacheEvent) {
        val tags = tags(cacheEvent.cacheKey)
        GlobalRum.get(sdkCore).addError(
            CACHE_ERROR_READ_MESSAGE,
            RumErrorSource.SOURCE,
            cacheEvent.exception,
            tags
        )
    }

    /** @inheritDoc */
    override fun onEviction(cacheEvent: CacheEvent) {
        // NoOp
    }

    /** @inheritDoc */
    override fun onHit(cacheEvent: CacheEvent) {
        // NoOp
    }

    /** @inheritDoc */
    override fun onCleared() {
        // NoOp
    }

    /** @inheritDoc */
    override fun onWriteAttempt(cacheEvent: CacheEvent) {
        // NoOp
    }

    /** @inheritDoc */
    override fun onWriteSuccess(cacheEvent: CacheEvent) {
        // NoOp
    }

    /** @inheritDoc */
    override fun onWriteException(cacheEvent: CacheEvent) {
        GlobalRum.get(sdkCore).addError(
            CACHE_ERROR_WRITE_MESSAGE,
            RumErrorSource.SOURCE,
            cacheEvent.exception,
            tags(cacheEvent.cacheKey)
        )
    }

    // endregion

    // region Internals

    private fun tags(cacheKey: CacheKey?): Map<String, String> {
        return if (cacheKey != null) {
            mapOf(
                CACHE_ENTRY_URI_TAG to cacheKey.uriString
            )
        } else {
            emptyMap()
        }
    }

    // endregion

    internal companion object {
        internal const val CACHE_ENTRY_URI_TAG: String = "cache_entry_uri"
        internal const val CACHE_ERROR_READ_MESSAGE = "Fresco Disk Cache Read error"
        internal const val CACHE_ERROR_WRITE_MESSAGE = "Fresco Disk Cache Write error"
    }
}
