/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import org.chromium.net.BidirectionalStream
import org.chromium.net.CronetEngine
import org.chromium.net.NetworkQualityRttListener
import org.chromium.net.NetworkQualityThroughputListener
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UrlRequest
import java.io.IOException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandlerFactory
import java.util.concurrent.Executor

@Suppress("TooManyFunctions") // The number of functions depends on Cronet implementation.
internal class DatadogCronetEngine(
    internal val delegate: CronetEngine,
    internal val apmNetworkInstrumentation: ApmNetworkInstrumentation?,
    internal val rumNetworkInstrumentation: RumNetworkInstrumentation?,
    internal val distributedTracingInstrumentation: ApmNetworkInstrumentation?
) : CronetEngine() {

    override fun newUrlRequestBuilder(
        url: String,
        callback: UrlRequest.Callback,
        executor: Executor
    ): UrlRequest.Builder {
        val datadogCallback = CronetRequestCallback(
            callback,
            apmNetworkInstrumentation,
            rumNetworkInstrumentation,
            distributedTracingInstrumentation
        )

        val requestContext = CronetRequestContext(
            url = url,
            engine = this,
            requestCallback = datadogCallback,
            executor = executor
        )

        return CronetUrlRequestBuilder(
            requestContext = requestContext,
            requestCallback = datadogCallback
        )
    }

    internal fun newDelegateUrlRequestBuilder(
        url: String,
        callback: UrlRequest.Callback,
        executor: Executor
    ): UrlRequest.Builder = delegate.newUrlRequestBuilder(url, callback, executor)

    // region simple delegation

    override fun getVersionString(): String? = delegate.versionString

    override fun shutdown() = delegate.shutdown()

    override fun startNetLogToFile(fileName: String?, logAll: Boolean) = delegate.startNetLogToFile(fileName, logAll)

    override fun stopNetLog() = delegate.stopNetLog()

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun getGlobalMetricsDeltas(): ByteArray? = delegate.globalMetricsDeltas

    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    override fun openConnection(url: URL?): URLConnection? = delegate.openConnection(url)

    override fun createURLStreamHandlerFactory(): URLStreamHandlerFactory? = delegate.createURLStreamHandlerFactory()

    override fun newBidirectionalStreamBuilder(
        url: String?,
        callback: BidirectionalStream.Callback?,
        executor: Executor?
    ): BidirectionalStream.Builder? = delegate.newBidirectionalStreamBuilder(url, callback, executor)

    override fun getActiveRequestCount(): Int = delegate.activeRequestCount

    override fun addRequestFinishedListener(listener: RequestFinishedInfo.Listener?) {
        listener?.let { delegate.addRequestFinishedListener(it) }
    }

    override fun removeRequestFinishedListener(
        listener: RequestFinishedInfo.Listener?
    ) = delegate.removeRequestFinishedListener(listener)

    override fun getHttpRttMs(): Int = delegate.httpRttMs

    override fun getTransportRttMs(): Int = delegate.transportRttMs

    override fun getDownstreamThroughputKbps(): Int = delegate.downstreamThroughputKbps

    override fun startNetLogToDisk(
        dirPath: String?,
        logAll: Boolean,
        maxSize: Int
    ) = delegate.startNetLogToDisk(dirPath, logAll, maxSize)

    override fun bindToNetwork(networkHandle: Long) = delegate.bindToNetwork(networkHandle)

    override fun getEffectiveConnectionType(): Int = delegate.effectiveConnectionType

    override fun configureNetworkQualityEstimatorForTesting(
        useLocalHostRequests: Boolean,
        useSmallerResponses: Boolean,
        disableOfflineCheck: Boolean
    ) = delegate.configureNetworkQualityEstimatorForTesting(
        useLocalHostRequests,
        useSmallerResponses,
        disableOfflineCheck
    )

    override fun addRttListener(listener: NetworkQualityRttListener?) = delegate.addRttListener(listener)

    override fun removeRttListener(listener: NetworkQualityRttListener?) = delegate.removeRttListener(listener)

    override fun addThroughputListener(
        listener: NetworkQualityThroughputListener?
    ) = delegate.addThroughputListener(listener)

    override fun removeThroughputListener(
        listener: NetworkQualityThroughputListener?
    ) = delegate.removeThroughputListener(listener)

    // endregion
}
