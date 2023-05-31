/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.resource

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.v2.api.SdkCore
import java.io.InputStream

/**
 * An [InputStream] wrapper that will report the stream read as a RUM Resource.
 *
 * @param delegate the actual [InputStream] to wrap
 * @param url the URL associated with the underlying resource, as you want it displayed in Datadog
 * @param sdkCore the [SdkCore] instance to report resources to
 */
@Suppress("ThrowingInternalException", "TooGenericExceptionCaught")
class RumResourceInputStream(
    val delegate: InputStream,
    val url: String,
    val sdkCore: SdkCore
) : InputStream() {

    internal val key: String = delegate.javaClass.simpleName +
        "@${System.identityHashCode(delegate)}"

    internal var size: Long = 0
    internal var failed: Boolean = false

    private var callStart: Long = 0L
    private var firstByte: Long = 0L
    private var lastByte: Long = 0L

    init {
        val rumMonitor = GlobalRum.get(sdkCore)
        rumMonitor.startResource(key, METHOD, url, emptyMap())
        callStart = System.nanoTime()
        if (rumMonitor is AdvancedRumMonitor) {
            rumMonitor.waitForResourceTiming(key)
        }
    }

    // region InputStream

    /** @inheritdoc */
    override fun read(): Int {
        if (firstByte == 0L) firstByte = System.nanoTime()
        return callWithErrorTracking(ERROR_READ) {
            @Suppress("UnsafeThirdPartyFunctionCall") // caller should handle the exception
            read().also {
                if (it >= 0) size++
                lastByte = System.nanoTime()
            }
        }
    }

    /** @inheritdoc */
    override fun read(b: ByteArray): Int {
        if (firstByte == 0L) firstByte = System.nanoTime()
        return callWithErrorTracking(ERROR_READ) {
            @Suppress("UnsafeThirdPartyFunctionCall") // caller should handle the exception
            read(b).also {
                if (it >= 0) size += it
                lastByte = System.nanoTime()
            }
        }
    }

    /** @inheritdoc */
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (firstByte == 0L) firstByte = System.nanoTime()
        return callWithErrorTracking(ERROR_READ) {
            @Suppress("UnsafeThirdPartyFunctionCall") // caller should handle the exception
            read(b, off, len).also {
                if (it >= 0) size += it
                lastByte = System.nanoTime()
            }
        }
    }

    /** @inheritdoc */
    override fun available(): Int {
        return callWithErrorTracking(ERROR_READ) {
            @Suppress("UnsafeThirdPartyFunctionCall") // caller should handle the exception
            available()
        }
    }

    /** @inheritdoc */
    override fun skip(n: Long): Long {
        @Suppress("UnsafeThirdPartyFunctionCall") // caller should handle the exception
        return callWithErrorTracking(ERROR_SKIP) {
            skip(n)
        }
    }

    /** @inheritdoc */
    override fun markSupported(): Boolean {
        return callWithErrorTracking(ERROR_READ) {
            markSupported()
        }
    }

    /** @inheritdoc */
    override fun mark(readlimit: Int) {
        return callWithErrorTracking(ERROR_MARK) {
            mark(readlimit)
        }
    }

    /** @inheritdoc */
    override fun reset() {
        @Suppress("UnsafeThirdPartyFunctionCall") // caller should handle the exception
        return callWithErrorTracking(ERROR_RESET) {
            reset()
        }
    }

    /** @inheritdoc */
    override fun close() {
        return callWithErrorTracking(ERROR_CLOSE) {
            @Suppress("UnsafeThirdPartyFunctionCall") // caller should handle the exception
            close()
            val monitor = GlobalRum.get(sdkCore)
            (monitor as? AdvancedRumMonitor)?.addResourceTiming(
                key,
                ResourceTiming(
                    downloadStart = firstByte - callStart,
                    downloadDuration = lastByte - firstByte
                )
            )
            monitor.stopResource(
                key,
                null,
                size,
                RumResourceKind.OTHER,
                emptyMap()
            )
        }
    }

    // endregion

    // region Internal

    private fun <T> callWithErrorTracking(
        errorMessage: String,
        operation: InputStream.() -> T
    ): T {
        try {
            return delegate.operation()
        } catch (e: Throwable) {
            if (!failed) {
                failed = true
                GlobalRum.get(sdkCore).stopResourceWithError(
                    key,
                    null,
                    errorMessage,
                    RumErrorSource.SOURCE,
                    e
                )
            }
            throw e
        }
    }

    // endregion

    internal companion object {
        internal const val METHOD: String = "GET"

        internal const val ERROR_CLOSE = "Error closing input stream"
        internal const val ERROR_MARK = "Error marking input stream"
        internal const val ERROR_READ = "Error reading from input stream"
        internal const val ERROR_RESET = "Error resetting input stream"
        internal const val ERROR_SKIP = "Error skipping bytes from input stream"
    }
}
