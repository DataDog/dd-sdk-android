/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet

import android.content.Context
import com.datadog.android.cronet.internal.DatadogRequestFinishedInfoListener
import com.datadog.android.cronet.internal.DatadogUrlRequestBuilder
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import org.chromium.net.BidirectionalStream
import org.chromium.net.ConnectionMigrationOptions
import org.chromium.net.CronetEngine
import org.chromium.net.DnsOptions
import org.chromium.net.ICronetEngineBuilder
import org.chromium.net.NetworkQualityRttListener
import org.chromium.net.NetworkQualityThroughputListener
import org.chromium.net.ProxyOptions
import org.chromium.net.QuicOptions
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UrlRequest
import java.io.IOException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandlerFactory
import java.util.Date
import java.util.concurrent.Executor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Datadog-instrumented wrapper for [CronetEngine] that adds RUM monitoring.
 * This wrapper delegates all Cronet functionality to the underlying engine while
 * intercepting network requests to report them as RUM resources.
 */
@Suppress("TooManyFunctions") // The number of functions depends on Cronet implementation.
class DatadogCronetEngine internal constructor(
    internal val delegate: CronetEngine,
    internal val rumResourceInstrumentation: RumResourceInstrumentation
) : CronetEngine() {

    /** @inheritDoc */
    override fun newUrlRequestBuilder(
        url: String,
        callback: UrlRequest.Callback,
        executor: Executor
    ): UrlRequest.Builder = DatadogUrlRequestBuilder(
        url = url,
        delegate = delegate.newUrlRequestBuilder(url, callback, executor),
        rumResourceInstrumentation = rumResourceInstrumentation
    )

    // region simple delegation

    /** @inheritDoc */
    override fun getVersionString(): String? = delegate.versionString

    /** @inheritDoc */
    override fun shutdown() = delegate.shutdown()

    /** @inheritDoc */
    override fun startNetLogToFile(fileName: String?, logAll: Boolean) = delegate.startNetLogToFile(fileName, logAll)

    /** @inheritDoc */
    override fun stopNetLog() = delegate.stopNetLog()

    /** @inheritDoc */
    @Deprecated("Deprecated in Java")
    override fun getGlobalMetricsDeltas(): ByteArray? {
        @Suppress("DEPRECATION")
        return delegate.globalMetricsDeltas
    }

    /** @inheritDoc */
    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    override fun openConnection(url: URL?): URLConnection? = delegate.openConnection(url)

    /** @inheritDoc */
    override fun createURLStreamHandlerFactory(): URLStreamHandlerFactory? = delegate.createURLStreamHandlerFactory()

    /** @inheritDoc */
    override fun newBidirectionalStreamBuilder(
        url: String?,
        callback: BidirectionalStream.Callback?,
        executor: Executor?
    ): BidirectionalStream.Builder? = delegate.newBidirectionalStreamBuilder(url, callback, executor)

    /** @inheritDoc */
    override fun getActiveRequestCount(): Int = delegate.activeRequestCount

    /** @inheritDoc */
    override fun addRequestFinishedListener(listener: RequestFinishedInfo.Listener?) =
        delegate.addRequestFinishedListener(listener)

    /** @inheritDoc */
    override fun removeRequestFinishedListener(listener: RequestFinishedInfo.Listener?) =
        delegate.removeRequestFinishedListener(listener)

    /** @inheritDoc */
    override fun getHttpRttMs(): Int = delegate.httpRttMs

    /** @inheritDoc */
    override fun getTransportRttMs(): Int = delegate.transportRttMs

    /** @inheritDoc */
    override fun getDownstreamThroughputKbps(): Int = delegate.downstreamThroughputKbps

    /** @inheritDoc */
    override fun startNetLogToDisk(dirPath: String?, logAll: Boolean, maxSize: Int) =
        delegate.startNetLogToDisk(dirPath, logAll, maxSize)

    /** @inheritDoc */
    override fun bindToNetwork(networkHandle: Long) = delegate.bindToNetwork(networkHandle)

    /** @inheritDoc */
    override fun getEffectiveConnectionType(): Int = delegate.effectiveConnectionType

    /** @inheritDoc */
    override fun configureNetworkQualityEstimatorForTesting(
        useLocalHostRequests: Boolean,
        useSmallerResponses: Boolean,
        disableOfflineCheck: Boolean
    ) = delegate.configureNetworkQualityEstimatorForTesting(
        useLocalHostRequests,
        useSmallerResponses,
        disableOfflineCheck
    )

    /** @inheritDoc */
    override fun addRttListener(listener: NetworkQualityRttListener?) = delegate.addRttListener(listener)

    /** @inheritDoc */
    override fun removeRttListener(listener: NetworkQualityRttListener?) = delegate.removeRttListener(listener)

    /** @inheritDoc */
    override fun addThroughputListener(listener: NetworkQualityThroughputListener?) =
        delegate.addThroughputListener(listener)

    /** @inheritDoc */
    override fun removeThroughputListener(listener: NetworkQualityThroughputListener?) =
        delegate.removeThroughputListener(listener)

    // endregion

    internal companion object {
        /** Network layer name used in RUM instrumentation for Cronet. */
        internal const val CRONET_NETWORK_INSTRUMENTATION_NAME = "Cronet"
    }

    /** @inheritDoc */
    @Suppress("TooManyFunctions") // The amount of functions is depend on Cronet
    class Builder : CronetEngine.Builder {

        /**
         * This constructor is made only for the testing purposes.
         *
         * @param iCronetEngineBuilder - an instance [ICronetEngineBuilder] usually made from [Context].
         * @param delegate - the delegate builder to wrap, defaults to a new CronetEngine.Builder
         */
        internal constructor(
            iCronetEngineBuilder: ICronetEngineBuilder,
            delegate: CronetEngine.Builder = CronetEngine.Builder(iCronetEngineBuilder)
        ) : super(iCronetEngineBuilder) {
            this.delegate = delegate
        }

        /** @inheritDoc */
        @ExperimentalRumApi
        constructor(
            context: Context,
            delegate: CronetEngine.Builder = CronetEngine.Builder(context)
        ) : super(context) {
            this.delegate = delegate
        }

        private val delegate: CronetEngine.Builder
        private var sdkInstanceName: String? = null
        private var listenerExecutor: Executor? = null
        private var rumResourceAttributesProvider: RumResourceAttributesProvider =
            NoOpRumResourceAttributesProvider()

        /**
         * Sets the [RumResourceAttributesProvider] to use to provide custom attributes to the RUM.
         * By default it won't attach any custom attributes.
         * @param rumResourceAttributesProvider the [RumResourceAttributesProvider] to use.
         */
        @ExperimentalRumApi
        fun setRumResourceAttributesProvider(rumResourceAttributesProvider: RumResourceAttributesProvider) = apply {
            this.rumResourceAttributesProvider = rumResourceAttributesProvider
        }

        /**
         * Set the SDK instance name to bind to, the default value is null.
         * @param sdkInstanceName SDK instance name to bind to, the default value is null.
         * Instrumentation won't be working until SDK instance is ready.
         */
        @ExperimentalRumApi
        fun setSdkInstanceName(sdkInstanceName: String) = apply {
            this.sdkInstanceName = sdkInstanceName
        }

        /**
         * Sets the executor for request finished listeners.
         * If not set, a default thread pool executor will be used.
         * @param executor the executor to use for request finished listeners
         */
        @ExperimentalRumApi
        fun setListenerExecutor(executor: Executor) = apply {
            this.listenerExecutor = executor
        }

        /** @inheritDoc */
        override fun getDefaultUserAgent(): String = delegate.defaultUserAgent

        /** @inheritDoc */
        override fun enableQuic(value: Boolean) = apply { delegate.enableQuic(value) }

        /** @inheritDoc */
        override fun setStoragePath(value: String?) = apply { delegate.setStoragePath(value) }

        /** @inheritDoc */
        override fun setUserAgent(userAgent: String?) = apply { delegate.setUserAgent(userAgent) }

        /** @inheritDoc */
        override fun setLibraryLoader(loader: LibraryLoader?) = apply { delegate.setLibraryLoader(loader) }

        /** @inheritDoc */
        override fun enableHttp2(value: Boolean) = apply { delegate.enableHttp2(value) }

        /** @inheritDoc */
        @Deprecated("Deprecated in Java")
        override fun enableSdch(value: Boolean) = apply {
            @Suppress("DEPRECATION")
            delegate.enableSdch(value)
        }

        /** @inheritDoc */
        override fun enableBrotli(value: Boolean) = apply { delegate.enableBrotli(value) }

        /** @inheritDoc */
        override fun enableHttpCache(cacheMode: Int, maxSize: Long) =
            apply { delegate.enableHttpCache(cacheMode, maxSize) }

        /** @inheritDoc */
        override fun addQuicHint(
            host: String?,
            port: Int,
            alternatePort: Int
        ) = apply {
            delegate.addQuicHint(host, port, alternatePort)
        }

        /** @inheritDoc */
        override fun addPublicKeyPins(
            hostName: String?,
            pinsSha256: Set<ByteArray?>?,
            includeSubdomains: Boolean,
            expirationDate: Date?
        ) = apply {
            @Suppress("UnsafeThirdPartyFunctionCall") // this method just delegates the call.
            delegate.addPublicKeyPins(hostName, pinsSha256, includeSubdomains, expirationDate)
        }

        /** @inheritDoc */
        override fun enablePublicKeyPinningBypassForLocalTrustAnchors(value: Boolean) = apply {
            delegate.enablePublicKeyPinningBypassForLocalTrustAnchors(value)
        }

        /** @inheritDoc */
        @Deprecated("Deprecated in Java")
        override fun setThreadPriority(priority: Int) = apply {
            @Suppress("DEPRECATION")
            delegate.setThreadPriority(priority)
        }

        /** @inheritDoc */
        override fun enableNetworkQualityEstimator(value: Boolean) = apply {
            delegate.enableNetworkQualityEstimator(value)
        }

        /** @inheritDoc */
        @QuicOptions.Experimental
        override fun setQuicOptions(quicOptions: QuicOptions?) = apply {
            delegate.setQuicOptions(quicOptions)
        }

        /** @inheritDoc */
        @QuicOptions.Experimental
        override fun setQuicOptions(quicOptionsBuilder: QuicOptions.Builder?) =
            apply { delegate.setQuicOptions(quicOptionsBuilder) }

        /** @inheritDoc */
        @DnsOptions.Experimental
        override fun setDnsOptions(dnsOptions: DnsOptions?) = apply { delegate.setDnsOptions(dnsOptions) }

        /** @inheritDoc */
        @DnsOptions.Experimental
        override fun setDnsOptions(dnsOptions: DnsOptions.Builder?) = apply { delegate.setDnsOptions(dnsOptions) }

        /** @inheritDoc */
        @ConnectionMigrationOptions.Experimental
        override fun setConnectionMigrationOptions(connectionMigrationOptions: ConnectionMigrationOptions?) = apply {
            delegate.setConnectionMigrationOptions(connectionMigrationOptions)
        }

        /** @inheritDoc */
        @ConnectionMigrationOptions.Experimental
        override fun setConnectionMigrationOptions(
            connectionMigrationOptionsBuilder: ConnectionMigrationOptions.Builder
        ) = apply {
            delegate.setConnectionMigrationOptions(connectionMigrationOptionsBuilder)
        }

        /** @inheritDoc */
        @ProxyOptions.Experimental
        override fun setProxyOptions(proxyOptions: ProxyOptions?) = apply {
            delegate.setProxyOptions(proxyOptions)
        }

        /** @inheritDoc */
        override fun build(): CronetEngine {
            val rumResourceInstrumentation = RumResourceInstrumentation(
                sdkInstanceName,
                networkInstrumentationName = CRONET_NETWORK_INSTRUMENTATION_NAME,
                rumResourceAttributesProvider = rumResourceAttributesProvider
            )

            val engine = DatadogCronetEngine(delegate.build(), rumResourceInstrumentation)

            engine.addRequestFinishedListener(
                DatadogRequestFinishedInfoListener(
                    executor = listenerExecutor ?: newListenerExecutor(),
                    rumResourceInstrumentation = rumResourceInstrumentation
                )
            )
            return engine
        }

        private companion object {
            // Exception thrown only for wrong arguments, but those ones are correct
            @Suppress("UnsafeThirdPartyFunctionCall")
            private fun newListenerExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
                0,
                Runtime.getRuntime().availableProcessors(),
                DEFAULT_KEEP_ALIVE_TIME_SECONDS,
                TimeUnit.SECONDS,
                SynchronousQueue()
            )

            private const val DEFAULT_KEEP_ALIVE_TIME_SECONDS = 60L
        }
    }
}
