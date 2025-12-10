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
 * @param delegate the underlying CronetEngine to wrap
 * @param rumResourceInstrumentation the instrumentation handler for RUM resource tracking
 */
@Suppress("TooManyFunctions") // The number of functions depends on Cronet implementation.
class DatadogCronetEngine(
    internal val delegate: CronetEngine,
    internal val rumResourceInstrumentation: RumResourceInstrumentation
) : CronetEngine() {

    /**
     * Builder wrapper for [CronetEngine] that adds Datadog instrumentation.
     * Datadog's instrumentation for Cronet is made using delegating instead of inheritance in order to make possible
     * for the customers to add several instrumentations at the same time. The [delegate] could be the pure
     * [CronetEngine.Builder] instance or another wrapper, provided by different vendor.
     */
    @Suppress("TooManyFunctions") // The amount of functions is depend on Cronet
    class Builder : CronetEngine.Builder {

        /**
         * This constructor is made only for the testing purposes.
         * Don't use it for production as the underlying method is made for internal usage as well.
         *
         * @param iCronetEngineBuilder - an instance [ICronetEngineBuilder] usually made from [Context].
         * @param delegate - the delegate builder to wrap, defaults to a new CronetEngine.Builder
         */
        @ExperimentalRumApi
        internal constructor(
            iCronetEngineBuilder: ICronetEngineBuilder,
            delegate: CronetEngine.Builder = CronetEngine.Builder(iCronetEngineBuilder)
        ) : super(iCronetEngineBuilder) {
            this.delegate = delegate
        }

        /**
         * Creates a new builder with Datadog instrumentation.
         *
         * @param context the Android context, which is used by {@link Builder} to retrieve the application context
         * @param delegate the delegate builder to wrap in case if several wrappers should be used,
         * defaults to a new CronetEngine.Builder
         */
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
        fun setRumResourceAttributesProvider(rumResourceAttributesProvider: RumResourceAttributesProvider) = apply {
            this.rumResourceAttributesProvider = rumResourceAttributesProvider
        }

        /**
         * Set the SDK instance name to bind to, the default value is null.
         * @param sdkInstanceName SDK instance name to bind to, the default value is null.
         * Instrumentation won't be working until SDK instance is ready.
         */
        fun setSdkInstanceName(sdkInstanceName: String) = apply {
            this.sdkInstanceName = sdkInstanceName
        }

        /**
         * Sets the executor for request finished listeners.
         * If not set, a default thread pool executor will be used.
         * @param executor the executor to use for request finished listeners
         */
        fun setListenerExecutor(executor: Executor) = apply {
            this.listenerExecutor = executor
        }

        /**
         * Builds a [DatadogCronetEngine] instance with RUM instrumentation.
         * @return the instrumented [CronetEngine]
         */
        override fun build(): CronetEngine {
            val rumResourceInstrumentation = RumResourceInstrumentation(
                sdkInstanceName,
                networkLayerName = CRONET_NETWORK_INSTRUMENTATION_NAME,
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

        // region simple delegation to CronetEngine.Builder

        /**
         * Constructs a User-Agent string including application name and version, system build version,
         * model and id, and Cronet version.
         * @return User-Agent string.
         */
        override fun getDefaultUserAgent(): String = delegate.defaultUserAgent

        /**
         * Sets whether <a href="https://www.chromium.org/quic">QUIC</a> protocol is enabled.
         * Defaults to enabled. If QUIC is enabled, then QUIC User Agent Id containing application
         * name and Cronet version is sent to the server.
         *
         * @param value `true` to enable QUIC, `false` to disable.
         * @return the builder to facilitate chaining.
         */
        override fun enableQuic(value: Boolean) = apply { delegate.enableQuic(value) }

        /**
         * Sets directory for HTTP Cache and Cookie Storage. The directory must exist.
         *
         * **NOTE:** Do not use the same storage directory with more than one `CronetEngine` at a time.
         * Access to the storage directory does not support concurrent
         * access by multiple `CronetEngine`s.
         *
         * @param value path to existing directory.
         * @return the builder to facilitate chaining.
         */
        override fun setStoragePath(value: String?) = apply { delegate.setStoragePath(value) }

        /**
         * Overrides the User-Agent header for all requests. An explicitly set User-Agent header
         * (set using [UrlRequest.Builder.addHeader]) will override a value set using this
         * function.
         *
         * @param userAgent the User-Agent string to use for all requests.
         * @return the builder to facilitate chaining.
         */
        override fun setUserAgent(userAgent: String?) = apply { delegate.setUserAgent(userAgent) }

        /**
         * Sets a [LibraryLoader] to be used to load the native library. If not set, the
         * library will be loaded using [System.loadLibrary].
         *
         * @param loader `LibraryLoader` to be used to load the native library.
         * @return the builder to facilitate chaining.
         */
        override fun setLibraryLoader(loader: LibraryLoader?) = apply { delegate.setLibraryLoader(loader) }

        /**
         * Sets whether <a href="https://tools.ietf.org/html/rfc7540">HTTP/2</a> protocol is
         * enabled. Defaults to enabled.
         *
         * @param value `true` to enable HTTP/2, `false` to disable.
         * @return the builder to facilitate chaining.
         */
        override fun enableHttp2(value: Boolean) = apply { delegate.enableHttp2(value) }

        /**
         * Deprecated SDCH is deprecated in Cronet M63. This method is a no-op.
         */
        @Deprecated("Deprecated in Java")
        override fun enableSdch(value: Boolean) = apply {
            @Suppress("DEPRECATION")
            delegate.enableSdch(value)
        }

        /**
         * Sets whether <a href="https://tools.ietf.org/html/rfc7932">Brotli</a> compression is
         * enabled. If enabled, Brotli will be advertised in Accept-Encoding request headers.
         * Defaults to disabled.
         *
         * @param value `true` to enable Brotli, `false` to disable.
         * @return the builder to facilitate chaining.
         */
        override fun enableBrotli(value: Boolean) = apply { delegate.enableBrotli(value) }

        /**
         * Enables or disables caching of HTTP data and other information like QUIC server
         * information.
         *
         * @param cacheMode control location and type of cached data. Must be one of
         * `HTTP_CACHE_DISABLED`, `HTTP_CACHE_*`.
         * @param maxSize maximum size in bytes used to cache data (advisory and maybe exceeded at
         * times).
         * @return the builder to facilitate chaining.
         */
        override fun enableHttpCache(cacheMode: Int, maxSize: Long) =
            apply { delegate.enableHttpCache(cacheMode, maxSize) }

        /**
         * Adds hint that `host` supports QUIC. Note that [enableHttpCache]
         * (`HTTP_CACHE_DISK`) is needed to take advantage of 0-RTT connection establishment
         * between sessions.
         *
         * @param host hostname of the server that supports QUIC.
         * @param port host of the server that supports QUIC.
         * @param alternatePort alternate port to use for QUIC.
         * @return the builder to facilitate chaining.
         */
        override fun addQuicHint(
            host: String?,
            port: Int,
            alternatePort: Int
        ) = apply {
            delegate.addQuicHint(host, port, alternatePort)
        }

        /**
         * Pins a set of public keys for a given host. By pinning a set of public keys, `pinsSha256`, communication
         * with `hostName` is required to authenticate with a
         * certificate with a public key from the set of pinned ones. An app can pin the public key
         * of the root certificate, any of the intermediate certificates or the end-entry
         * certificate. Authentication will fail and secure communication will not be established if
         * none of the public keys is present in the host's certificate chain, even if the host
         * attempts to authenticate with a certificate allowed by the device's trusted store of
         * certificates.
         *
         * Calling this method multiple times with the same host name overrides the previously
         * set pins for the host.
         *
         * More information about the public key pinning can be found in <a
         * href="https://tools.ietf.org/html/rfc7469">RFC 7469</a>.
         *
         * @param hostName name of the host to which the public keys should be pinned. A host that
         * consists only of digits and the dot character is treated as invalid.
         * @param pinsSha256 a set of pins. Each pin is the SHA-256 cryptographic hash of the
         * DER-encoded ASN.1 representation of the Subject Public Key Info (SPKI) of the host's
         * X.509 certificate. Use [java.security.cert.Certificate.getPublicKey]
         * and [java.security.Key.getEncoded]
         * to obtain DER-encoded ASN.1 representation of the SPKI. Although, the method does not
         * mandate the presence of the backup pin that can be used if the control of the primary
         * private key has been lost, it is highly recommended to supply one.
         * @param includeSubdomains indicates whether the pinning policy should be applied to
         *         subdomains
         * of `hostName`.
         * @param expirationDate specifies the expiration date for the pins.
         * @return the builder to facilitate chaining.
         * @throws NullPointerException if any of the input parameters are `null`.
         * @throws IllegalArgumentException if the given host name is invalid or `pinsSha256`
         * contains a byte array that does not represent a valid SHA-256 hash.
         */
        override fun addPublicKeyPins(
            hostName: String?,
            pinsSha256: Set<ByteArray?>?,
            includeSubdomains: Boolean,
            expirationDate: Date?
        ) = apply {
            @Suppress("UnsafeThirdPartyFunctionCall") // this method just delegates the call.
            delegate.addPublicKeyPins(hostName, pinsSha256, includeSubdomains, expirationDate)
        }

        /**
         * Enables or disables public key pinning bypass for local trust anchors. Disabling the
         * bypass for local trust anchors is highly discouraged since it may prohibit the app from
         * communicating with the pinned hosts. E.g., a user may want to send all traffic through an
         * SSL enabled proxy by changing the device proxy settings and adding the proxy certificate
         * to the list of local trust anchor. Disabling the bypass will most likely prevent the app
         * from sending any traffic to the pinned hosts. For more information see 'How does key
         * pinning interact with local proxies and filters?' at
         * https://www.chromium.org/Home/chromium-security/security-faq
         *
         * @param value `true` to enable the bypass, `false` to disable.
         * @return the builder to facilitate chaining.
         */
        override fun enablePublicKeyPinningBypassForLocalTrustAnchors(value: Boolean) = apply {
            delegate.enablePublicKeyPinningBypassForLocalTrustAnchors(value)
        }

        /**
         * Sets the thread priority of Cronet's internal thread.
         *
         * Deprecated On modern versions of Cronet, this method does nothing.
         * @param priority the thread priority of Cronet's internal thread. A Linux priority level,
         *     from -20 for highest scheduling priority to 19 for lowest scheduling priority. For
         *     more information on values, see [android.os.Process.setThreadPriority] and
         *     `THREAD_PRIORITY_DEFAULT`, `THREAD_PRIORITY_*` values.
         * @return the builder to facilitate chaining.
         */
        @Deprecated("Deprecated in Java")
        override fun setThreadPriority(priority: Int) = apply {
            @Suppress("DEPRECATION")
            delegate.setThreadPriority(priority)
        }

        /**
         * Enables the network quality estimator, which collects and reports measurements of round
         * trip time (RTT) and downstream throughput at various layers of the network stack. After
         * enabling the estimator, listeners of RTT and throughput can be added with
         * [addRttListener] and
         * [addThroughputListener] and removed with [removeRttListener] and
         * [removeThroughputListener]. The estimator uses memory and CPU only when enabled.
         *
         * @param value `true` to enable network quality estimator, `false` to disable.
         * @return the builder to facilitate chaining.
         */
        override fun enableNetworkQualityEstimator(value: Boolean) = apply {
            delegate.enableNetworkQualityEstimator(value)
        }

        /**
         * Configures the behavior of Cronet when using QUIC. For more details, see documentation of
         * [QuicOptions] and the individual methods of [QuicOptions.Builder].
         *
         * Only relevant if [enableQuic] is enabled.
         *
         * @return the builder to facilitate chaining.
         */
        @QuicOptions.Experimental
        override fun setQuicOptions(quicOptions: QuicOptions?) = apply {
            delegate.setQuicOptions(quicOptions)
        }

        /** @see setQuicOptions */
        @QuicOptions.Experimental
        override fun setQuicOptions(quicOptionsBuilder: QuicOptions.Builder?) =
            apply { delegate.setQuicOptions(quicOptionsBuilder) }

        /**
         * Configures the behavior of hostname lookup. For more details, see documentation of
         * [DnsOptions] and the individual methods of [DnsOptions.Builder].
         *
         * Only relevant if [enableQuic] is enabled.
         *
         * @return the builder to facilitate chaining.
         */
        @DnsOptions.Experimental
        override fun setDnsOptions(dnsOptions: DnsOptions?) = apply { delegate.setDnsOptions(dnsOptions) }

        /** @see setDnsOptions */
        @DnsOptions.Experimental
        override fun setDnsOptions(dnsOptions: DnsOptions.Builder?) = apply { delegate.setDnsOptions(dnsOptions) }

        /**
         * Configures the behavior of connection migration. For more details, see documentation of
         * [ConnectionMigrationOptions] and the individual methods of
         * [ConnectionMigrationOptions.Builder].
         *
         * Only relevant if [enableQuic] is enabled.
         *
         * @return the builder to facilitate chaining.
         */
        @ConnectionMigrationOptions.Experimental
        override fun setConnectionMigrationOptions(connectionMigrationOptions: ConnectionMigrationOptions?) = apply {
            delegate.setConnectionMigrationOptions(connectionMigrationOptions)
        }

        /** @see setConnectionMigrationOptions */
        @ConnectionMigrationOptions.Experimental
        override fun setConnectionMigrationOptions(
            connectionMigrationOptionsBuilder: ConnectionMigrationOptions.Builder
        ) = apply {
            delegate.setConnectionMigrationOptions(connectionMigrationOptionsBuilder)
        }

        /**
         * Configures proxying behavior for connection establishment. This affects all connections
         * established by a [CronetEngine] as a consequence of [UrlRequest] being
         * started. For more details, see the documentation of [ProxyOptions].
         *
         * Warning: DO NOT USE without reaching out to Cronet maintainers first. This is
         * experimental and subject to change.
         *
         * Note: The Android OS can already define a "system" proxy configurations. This config
         * might have been obtained by the user, from some enterprise profile configuration, or
         * (most likely) from some network autoconfiguration (e.g., Web Proxy Auto-Discovery
         * Protocol). Proxy configurations configured via this API and system ones are mutually
         * exclusive. When specifying [ProxyOptions] you are overriding the system
         * configuration, this can cause connectivity problems (e.g., the internet might no longer
         * be reachable).
         * This could be done: either, by chaining them to the ones provided by the app; or, by
         * using them in place of a DIRECT fallback, if that has been specified by the app.
         *
         * @param proxyOptions ProxyOptions to be used for connections established by the
         *     [CronetEngine] created by this builder.
         * @return the builder to facilitate chaining.
         */
        @ProxyOptions.Experimental
        override fun setProxyOptions(proxyOptions: ProxyOptions?) = apply {
            delegate.setProxyOptions(proxyOptions)
        }

        // endregion
        companion object {
            //  Exception thrown only for wrong arguments, but those ones are correct
            @Suppress("UnsafeThirdPartyFunctionCall")
            private fun newListenerExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
                0,
                Int.MAX_VALUE,
                DEFAULT_KEEP_ALIVE_TIME_SECONDS,
                TimeUnit.SECONDS,
                SynchronousQueue()
            )

            private const val DEFAULT_KEEP_ALIVE_TIME_SECONDS = 60L
        }
    }

    /**
     * Creates a new URL request builder with RUM instrumentation.
     * @param url the URL to request
     * @param callback the callback to receive request events
     * @param executor the executor for callback execution
     * @return an instrumented UrlRequest.Builder
     */
    override fun newUrlRequestBuilder(
        url: String,
        callback: UrlRequest.Callback,
        executor: Executor
    ): UrlRequest.Builder? {
        return DatadogUrlRequestBuilder(
            url = url,
            delegate = delegate.newUrlRequestBuilder(url, callback, executor),
            rumResourceInstrumentation = rumResourceInstrumentation
        )
    }

    // region simple delegation

    /** @return a human-readable version string of the engine. */
    override fun getVersionString(): String? = delegate.versionString

    /**
     * Shuts down the [CronetEngine] if there are no active requests, otherwise throws an
     * exception.
     *
     * Cannot be called on network thread - the thread Cronet calls into Executor on (which is
     * different from the thread the Executor invokes callbacks on). May block until all the
     * `CronetEngine`'s resources have been cleaned up.
     */
    override fun shutdown() = delegate.shutdown()

    /**
     * Starts NetLog logging to a file. The NetLog will contain events emitted by all live
     * CronetEngines. The NetLog is useful for debugging. The file can be viewed using a Chrome
     * browser navigated to chrome://net-internals/#import
     *
     * @param fileName the complete file path. It must not be empty. If the file exists, it is
     * truncated before starting. If actively logging, this method is ignored.
     * @param logAll `true` to include basic events, user cookies, credentials and all
     * transferred bytes in the log. This option presents a privacy risk, since it exposes the
     * user's credentials, and should only be used with the user's consent and in situations where
     * the log won't be public. `false` to just include basic events.
     */
    override fun startNetLogToFile(fileName: String?, logAll: Boolean) = delegate.startNetLogToFile(fileName, logAll)

    /**
     * Stops NetLog logging and flushes file to disk. If a logging session is not in progress, this
     * call is ignored.
     */
    override fun stopNetLog() = delegate.stopNetLog()

    /**
     * Deprecated In modern versions of Cronet, this will always return an empty array. In older
     * versions, this used to return a serialized protobuf containing metrics data.
     */
    @Deprecated("Deprecated in Java")
    override fun getGlobalMetricsDeltas(): ByteArray? {
        @Suppress("DEPRECATION")
        return delegate.globalMetricsDeltas
    }

    /**
     * Establishes a new connection to the resource specified by the [URL] `url`.
     *
     * **Note:** Cronet's [java.net.HttpURLConnection] implementation is subject to
     * certain limitations, see [createURLStreamHandlerFactory] for details.
     *
     * @param url URL of resource to connect to.
     * @return an [java.net.HttpURLConnection] instance implemented by this CronetEngine.
     * @throws java.io.IOException if an error occurs while opening the connection.
     */
    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    override fun openConnection(url: URL?): URLConnection? = delegate.openConnection(url)

    /**
     * Creates a [URLStreamHandlerFactory] to handle HTTP and HTTPS traffic. An instance of
     * this class can be installed via [URL.setURLStreamHandlerFactory] thus using this
     * CronetEngine by default for all requests created via [URL.openConnection].
     *
     * Cronet does not use certain HTTP features provided via the system:
     *
     * - the HTTP cache installed via `HttpResponseCache.install(java.io.File, long)`
     * - the HTTP authentication method installed via [java.net.Authenticator.setDefault]
     * - the HTTP cookie storage installed via [java.net.CookieHandler.setDefault]
     *
     * While Cronet supports and encourages requests using the HTTPS protocol, Cronet does not
     * provide support for the `HttpsURLConnection` API. This lack of support also includes
     * not using certain HTTPS features provided via the system:
     *
     * - the HTTPS hostname verifier installed via
     *       `HttpsURLConnection.setDefaultHostnameVerifier(javax.net.ssl.HostnameVerifier)`
     * - the HTTPS socket factory installed via
     *       `HttpsURLConnection.setDefaultSSLSocketFactory(javax.net.ssl.SSLSocketFactory)`
     *
     * @return an [URLStreamHandlerFactory] instance implemented by this CronetEngine.
     */
    override fun createURLStreamHandlerFactory(): URLStreamHandlerFactory? = delegate.createURLStreamHandlerFactory()

    /**
     * Creates a builder for [BidirectionalStream] objects. All callbacks for generated
     * `BidirectionalStream` objects will be invoked on `executor`. `executor` must not
     * run tasks on the current thread, otherwise the networking operations may block and exceptions
     * may be thrown at shutdown time.
     *
     * @param url URL for the generated streams.
     * @param callback the [BidirectionalStream.Callback] object that gets invoked upon
     * different events occurring.
     * @param executor the [Executor] on which `callback` methods will be invoked.
     * @return the created builder.
     *
     * {@hide}
     */
    override fun newBidirectionalStreamBuilder(
        url: String?,
        callback: BidirectionalStream.Callback?,
        executor: Executor?
    ): BidirectionalStream.Builder? = delegate.newBidirectionalStreamBuilder(url, callback, executor)

    /**
     * Returns the number of active requests.
     *
     * A request becomes "active" in UrlRequest.start(), assuming that method
     * does not throw an exception. It becomes inactive when all callbacks have
     * returned and no additional callbacks can be triggered in the future. In
     * practice, that means the request is inactive once
     * onSucceeded/onCanceled/onFailed has returned and all request finished
     * listeners have returned.
     *
     * <a href="https://developer.android.com/guide/topics/connectivity/cronet/lifecycle">Cronet
     *         requests's lifecycle</a> for more information.
     */
    override fun getActiveRequestCount(): Int = delegate.activeRequestCount

    /**
     * Registers a listener that gets called after the end of each request with the request info.
     *
     * The listener is called on an [java.util.concurrent.Executor] provided by the
     * listener.
     *
     * @param listener the listener for finished requests.
     */
    override fun addRequestFinishedListener(listener: RequestFinishedInfo.Listener?) =
        delegate.addRequestFinishedListener(listener)

    /**
     * Removes a finished request listener.
     *
     * @param listener the listener to remove.
     */
    override fun removeRequestFinishedListener(listener: RequestFinishedInfo.Listener?) =
        delegate.removeRequestFinishedListener(listener)

    /**
     * Returns the HTTP RTT estimate (in milliseconds) computed by the network quality estimator.
     * Set to `CONNECTION_METRIC_UNKNOWN` if the value is unavailable. This must be called
     * after
     * [Builder.enableNetworkQualityEstimator], and will throw an exception otherwise.
     *
     * @return Estimate of the HTTP RTT in milliseconds.
     */
    override fun getHttpRttMs(): Int = delegate.httpRttMs

    /**
     * Returns the transport RTT estimate (in milliseconds) computed by the network quality
     * estimator. Set to `CONNECTION_METRIC_UNKNOWN` if the value is unavailable. This must
     * be called after [Builder.enableNetworkQualityEstimator], and will throw an exception
     * otherwise.
     *
     * @return Estimate of the transport RTT in milliseconds.
     */
    override fun getTransportRttMs(): Int = delegate.transportRttMs

    /**
     * Returns the downstream throughput estimate (in kilobits per second) computed by the network
     * quality estimator. Set to `CONNECTION_METRIC_UNKNOWN` if the value is unavailable.
     * This must be called after [Builder.enableNetworkQualityEstimator], and will throw an
     * exception otherwise.
     *
     * @return Estimate of the downstream throughput in kilobits per second.
     */
    override fun getDownstreamThroughputKbps(): Int = delegate.downstreamThroughputKbps

    /**
     * Starts NetLog logging to a specified directory with a bounded size. The NetLog will contain
     * events emitted by all live CronetEngines. The NetLog is useful for debugging. Once logging
     * has stopped [stopNetLog], the data will be written to netlog.json in `dirPath`.
     * If logging is interrupted, you can stitch the files found in .inprogress subdirectory
     * manually using:
     * https://chromium.googlesource.com/chromium/src/+/main/net/tools/stitch_net_log_files.py. The
     * log can be viewed using a Chrome browser navigated to chrome://net-internals/#import.
     *
     * @param dirPath the directory where the netlog.json file will be created. dirPath must already
     * exist. NetLog files must not exist in the directory. If actively logging, this method is
     * ignored.
     * @param logAll `true` to include basic events, user cookies, credentials and all
     * transferred bytes in the log. This option presents a privacy risk, since it exposes the
     * user's credentials, and should only be used with the user's consent and in situations where
     * the log won't be public. `false` to just include basic events.
     * @param maxSize the maximum total disk space in bytes that should be used by NetLog. Actual
     *         disk
     * space usage may exceed this limit slightly.
     */
    override fun startNetLogToDisk(dirPath: String?, logAll: Boolean, maxSize: Int) =
        delegate.startNetLogToDisk(dirPath, logAll, maxSize)

    /**
     * Binds the engine to the specified network handle. All requests created through this engine
     * will use the network associated to this handle. If this network disconnects all requests will
     * fail, the exact error will depend on the stage of request processing when the network
     * disconnects. Network handles can be obtained through `Network.getNetworkHandle`. Only
     * available starting from Android Marshmallow.
     *
     * @param networkHandle the network handle to bind the engine to. Specify
     * `UNBIND_NETWORK_HANDLE` to unbind.
     */
    override fun bindToNetwork(networkHandle: Long) = delegate.bindToNetwork(networkHandle)

    /**
     * Returns an estimate of the effective connection type computed by the network quality
     * estimator. Call [Builder.enableNetworkQualityEstimator] to begin computing this value.
     *
     * @return the estimated connection type. The returned value is one of
     * `EFFECTIVE_CONNECTION_TYPE_UNKNOWN`, `EFFECTIVE_CONNECTION_TYPE_*`.
     */
    override fun getEffectiveConnectionType(): Int = delegate.effectiveConnectionType

    /**
     * Configures the network quality estimator for testing. This must be called before round trip
     * time and throughput listeners are added, and after the network quality estimator has been
     * enabled.
     *
     * @param useLocalHostRequests include requests to localhost in estimates.
     * @param useSmallerResponses include small responses in throughput estimates.
     * @param disableOfflineCheck when set to true, disables the device offline checks when
     *         computing
     * the effective connection type or when writing the prefs.
     */
    override fun configureNetworkQualityEstimatorForTesting(
        useLocalHostRequests: Boolean,
        useSmallerResponses: Boolean,
        disableOfflineCheck: Boolean
    ) = delegate.configureNetworkQualityEstimatorForTesting(
        useLocalHostRequests,
        useSmallerResponses,
        disableOfflineCheck
    )

    /**
     * Registers a listener that gets called whenever the network quality estimator witnesses a
     * sample round trip time. This must be called after
     * [Builder.enableNetworkQualityEstimator], and with throw an exception otherwise. Round trip
     * times may be recorded at various layers of the network stack, including TCP, QUIC, and at the
     * URL request layer. The listener is called on the
     * [java.util.concurrent.Executor] that is passed to
     * [Builder.enableNetworkQualityEstimator].
     *
     * @param listener the listener of round trip times.
     */
    override fun addRttListener(listener: NetworkQualityRttListener?) = delegate.addRttListener(listener)

    /**
     * Removes a listener of round trip times if previously registered with [addRttListener].
     * This should be called after a [NetworkQualityRttListener] is added in order to stop
     * receiving observations.
     *
     * @param listener the listener of round trip times.
     */
    override fun removeRttListener(listener: NetworkQualityRttListener?) = delegate.removeRttListener(listener)

    /**
     * Registers a listener that gets called whenever the network quality estimator witnesses a
     * sample throughput measurement. This must be called after
     * [Builder.enableNetworkQualityEstimator]. Throughput observations are computed by measuring
     * bytes read over the active network interface at times when at least one URL response is being
     * received. The listener is called on the [java.util.concurrent.Executor] that is passed
     * to [Builder.enableNetworkQualityEstimator].
     *
     * @param listener the listener of throughput.
     */
    override fun addThroughputListener(listener: NetworkQualityThroughputListener?) =
        delegate.addThroughputListener(listener)

    /**
     * Removes a listener of throughput. This should be called after a
     * [NetworkQualityThroughputListener] is added with [addThroughputListener] in order to
     * stop receiving observations.
     *
     * @param listener the listener of throughput.
     */
    override fun removeThroughputListener(listener: NetworkQualityThroughputListener?) =
        delegate.removeThroughputListener(listener)

    // endregion

    companion object {
        /** Network layer name used in RUM instrumentation for Cronet. */
        internal const val CRONET_NETWORK_INSTRUMENTATION_NAME = "Cronet"
    }
}
