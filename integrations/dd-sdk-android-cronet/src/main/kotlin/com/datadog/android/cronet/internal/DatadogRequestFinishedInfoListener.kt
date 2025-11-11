package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.RequestInfo
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import org.chromium.net.RequestFinishedInfo
import java.io.IOException
import java.util.Date
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

internal class DatadogRequestFinishedInfoListener(
    executor: Executor,
    internal val rumResourceInstrumentation: RumResourceInstrumentation
) : RequestFinishedInfo.Listener(executor) {

    override fun onRequestFinished(finishedInfo: RequestFinishedInfo) {
        val requestInfo = finishedInfo.annotations?.firstOrNull { it is RequestInfo } as? RequestInfo

        if (requestInfo == null) {
            rumResourceInstrumentation.reportInstrumentationError(
                "Unable to instrument RUM resource without the request info"
            )
            return
        }

        val resourceTiming = buildTiming(finishedInfo.metrics)
        rumResourceInstrumentation.sendTiming(requestInfo, resourceTiming)

        when (finishedInfo.finishedReason) {
            RequestFinishedInfo.FAILED -> {
                rumResourceInstrumentation.stopResourceWithError(
                    requestInfo = requestInfo,
                    throwable = finishedInfo.exception ?: IOException("Request failed")
                )
            }

            RequestFinishedInfo.CANCELED -> {
                rumResourceInstrumentation.stopResourceWithError(
                    requestInfo = requestInfo,
                    throwable = IOException("Request was cancelled")
                )
            }

            RequestFinishedInfo.SUCCEEDED -> {
                val responseInfo = finishedInfo.responseInfo?.let { CronetResponseInfo(it) }

                if (responseInfo == null) {
                    rumResourceInstrumentation.stopResourceWithError(
                        requestInfo = requestInfo,
                        throwable = IllegalStateException("Received null response")
                    )
                } else {
                    rumResourceInstrumentation.stopResource(
                        requestInfo = requestInfo,
                        responseInfo = responseInfo
                    )
                }
            }
        }
    }

    private fun buildTiming(metrics: RequestFinishedInfo.Metrics): ResourceTiming {
        val connectStartMs = metrics.connectStart - metrics.requestStart
        val connectDurationMs = metrics.connectEnd - metrics.connectStart

        val dnsStartMs = metrics.dnsStart - metrics.requestStart
        val dnsDurationMs = metrics.dnsEnd - metrics.dnsStart

        val sslStartMs = metrics.sslStart - metrics.requestStart
        val sslDurationMs = metrics.sslEnd - metrics.sslStart

        val timeToFirstHeaderByteMs = metrics.ttfbMs ?: 0L
        val responseStartMs = metrics.responseStart?.time ?: 0L
        val requestStartMs = metrics.requestStart?.time ?: 0L
        val headersFetchingStartedMs = if (timeToFirstHeaderByteMs > 0) timeToFirstHeaderByteMs else 0
        val headersFetchDurationMs = if (headersFetchingStartedMs > 0 && requestStartMs > 0 && responseStartMs > 0) {
            /*
             The Cronet engine provides [metrics.ttfbMs] as the  ([metrics.responseStart] - [metrics.requestStart])
             so the following line will always be equal to 0. But let's keep it in case this issue is fixed in a future
             version of Cronet.
             */
            responseStartMs - (requestStartMs + timeToFirstHeaderByteMs)
        } else {
            0
        }

        val downloadStartMs = metrics.responseStart - metrics.requestStart
        val downloadDurationMs = metrics.requestEnd - metrics.responseStart

        return ResourceTiming(
            connectStart = connectStartMs.toNanos(),
            connectDuration = connectDurationMs.toNanos(),

            dnsStart = dnsStartMs.toNanos(),
            dnsDuration = dnsDurationMs.toNanos(),

            sslStart = sslStartMs.toNanos(),
            sslDuration = sslDurationMs.toNanos(),

            firstByteStart = headersFetchingStartedMs.toNanos(),
            firstByteDuration = headersFetchDurationMs.toNanos(),

            downloadStart = downloadStartMs.toNanos(),
            downloadDuration = downloadDurationMs.toNanos()
        )
    }

    companion object {

        internal operator fun Long?.plus(other: Long?): Long? {
            return if (this == null || other == null) null else this + other
        }

        internal operator fun Long?.minus(other: Long?): Long {
            return if (this == null || other == null) 0L else this - other
        }

        internal operator fun Date?.minus(other: Date?): Long {
            val thisTime = this?.time
            val otherTime = other?.time
            return if (thisTime == null || otherTime == null) 0L else thisTime - otherTime
        }

        private fun Long.toNanos(): Long = TimeUnit.MILLISECONDS.toNanos(this)
    }
}
