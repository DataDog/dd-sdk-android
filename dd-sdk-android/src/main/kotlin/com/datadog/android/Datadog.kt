/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android

import android.app.Application
import android.content.Context
import android.os.Build
import com.datadog.android.core.internal.data.upload.DataUploadHandlerThread
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleCallback
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.net.DataOkHttpUploader
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.GzipRequestInterceptor
import com.datadog.android.core.internal.net.NetworkTimeInterceptor
import com.datadog.android.core.internal.net.info.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.core.internal.net.info.CallbackNetworkInfoProvider
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.core.internal.time.DatadogTimeProvider
import com.datadog.android.core.internal.time.MutableTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.error.internal.DatadogExceptionHandler
import com.datadog.android.log.EndpointUpdateStrategy
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogFileStrategy
import com.datadog.android.log.internal.user.DatadogUserInfoProvider
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.tracing.TracerBuilder
import com.datadog.android.tracing.internal.AndroidTracerBuilder
import com.datadog.android.tracing.internal.domain.TracingFileStrategy
import datadog.opentracing.DDSpan
import java.lang.IllegalStateException
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * This class initializes the Datadog SDK, and sets up communication with the server.
 */
@Suppress("TooManyFunctions")
object Datadog {

    /**
     * The endpoint for our US based servers, used by default by the SDK.
     * @see [initialize]
     */
    @Suppress("MemberVisibilityCanBePrivate")
    const val DATADOG_US: String = "https://mobile-http-intake.logs.datadoghq.com"

    /**
     * The endpoint for our Europe based servers.
     * Use this in your call to [initialize] if you log on
     * [app.datadoghq.eu](https://app.datadoghq.eu/) instead of
     * [app.datadoghq.com](https://app.datadoghq.com/)
     */
    @Suppress("MemberVisibilityCanBePrivate")
    const val DATADOG_EU: String = "https://mobile-http-intake.logs.datadoghq.eu"

    private const val TAG = "Datadog"

    internal const val NETWORK_TIMEOUT_MS = DatadogTimeProvider.MAX_OFFSET_DEVIATION / 2
    internal const val LOG_UPLOAD_THREAD_NAME = "ddog-logs-upload"

    private var initialized: Boolean = false
    private lateinit var clientToken: String
    private lateinit var logStrategy: PersistenceStrategy<Log>
    private lateinit var networkInfoProvider: NetworkInfoProvider
    private lateinit var systemInfoProvider: BroadcastReceiverSystemInfoProvider
    private lateinit var handlerThread: DataUploadHandlerThread
    private lateinit var contextRef: WeakReference<Context>
    private lateinit var uploader: DataUploader
    private lateinit var timeProvider: MutableTimeProvider
    private lateinit var userInfoProvider: MutableUserInfoProvider
    private lateinit var tracingStrategy: PersistenceStrategy<DDSpan>

    internal var packageName: String = ""
        private set
    internal var packageVersion: String = ""
        private set
    internal var libraryVerbosity = Int.MAX_VALUE
        private set

    /**
     * Initializes the Datadog SDK.
     * @param context your application context
     * @param clientToken your API key of type Client Token
     * @param endpointUrl (optional) the endpoint url to target, or null to use the default. Possible values are
     * [DATADOG_US], [DATADOG_EU] or a custom endpoint.
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        context: Context,
        clientToken: String,
        endpointUrl: String? = null
    ) {
        if (initialized) {
            devLogger.w(
                "The Datadog library has already been initialized.",
                IllegalStateException("The Datadog library has already been initialized.")
            )
            return
        }

        val appContext = context.applicationContext

        initSdkCredentials(appContext, clientToken)

        initNetworkInfoProvider(appContext)

        logStrategy =
            LogFileStrategy(appContext)

        tracingStrategy = TracingFileStrategy(appContext)

        // prepare time management
        timeProvider = DatadogTimeProvider(appContext)

        // Prepare user info management
        userInfoProvider = DatadogUserInfoProvider()

        // init the SystemInfoProvider
        systemInfoProvider = BroadcastReceiverSystemInfoProvider()

        // setup the system info provider
        setupTheSystemInfoProvider(appContext)

        // setup the logs uploader
        setupLogsUploader(endpointUrl)

        // setup the process lifecycle monitor
        setupLifecycleMonitorCallback(appContext)

        initialized = true

        // setup the exception handler
        // We set this up last.
        // We don't want to catch any exception that might throw during the initialisation)
        setupTheExceptionHandler(appContext)
    }

    /**
     * Changes the endpoint to which data is sent.
     * @param endpointUrl the endpoint url to target, or null to use the default.
     * Possible values are [DATADOG_US], [DATADOG_EU] or a custom endpoint.
     * @param strategy the strategy defining how to handle logs created previously.
     * Because logs are sent asynchronously, some logs intended for the previous endpoint
     * might still be yet to sent.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @JvmStatic
    @Deprecated("This was only meant as an internal feature and is not needed anymore.")
    fun setEndpointUrl(endpointUrl: String, strategy: EndpointUpdateStrategy) {
        devLogger.w(String.format(Locale.US, MESSAGE_DEPRECATED, "setEndpointUrl()"))
    }

    // Stop all Datadog work (for test purposes).
    @Suppress("unused")
    private fun stop() {
        checkInitialized()
        handlerThread.quitSafely()
        contextRef.get()?.let { networkInfoProvider.unregister(it) }
        contextRef.clear()
        initialized = false
    }

    /**
     * Sets the verbosity of the Datadog library.
     *
     * Messages with a priority level equal or above the given level will be sent to Android's
     * Logcat.
     *
     * @param level one of the Android [Log] constants ([Log.VERBOSE], [Log.DEBUG], [Log.INFO],
     * [Log.WARN], [Log.ERROR], [Log.ASSERT]).
     */
    @JvmStatic
    fun setVerbosity(level: Int) {
        libraryVerbosity = level
    }

    /**
     * Sets the user information.
     *
     * @param id (nullable) a unique user identifier (relevant to your business domain)
     * @param name (nullable) the user name or alias
     * @param email (nullable) the user email
     */
    @JvmStatic
    @JvmOverloads
    fun setUserInfo(
        id: String? = null,
        name: String? = null,
        email: String? = null
    ) {
        userInfoProvider.setUserInfo(UserInfo(id, name, email))
    }

    /**
     * Creates a tracer builder instance.
     * @param serviceName as the name of a set of processes that do the same job.
     * Used for grouping stats for your application.
     */
    @JvmStatic
    fun tracerBuilder(serviceName: String): TracerBuilder {
        return AndroidTracerBuilder(serviceName, getTracingStrategy().getWriter())
    }

    // region Internal Provider

    internal fun getLogStrategy(): PersistenceStrategy<Log> {
        checkInitialized()
        return logStrategy
    }

    internal fun getLogUploader(): DataUploader {
        checkInitialized()
        return uploader
    }

    internal fun getNetworkInfoProvider(): NetworkInfoProvider {
        return networkInfoProvider
    }

    internal fun getTimeProvider(): TimeProvider {
        return timeProvider
    }

    internal fun getUserInfoProvider(): UserInfoProvider {
        return userInfoProvider
    }

    internal fun getTracingStrategy(): PersistenceStrategy<DDSpan> {
        checkInitialized()
        return tracingStrategy
    }
    // endregion

    // region Internal Initialization

    @Suppress("CheckInternal")
    private fun checkInitialized() {
        check(initialized) { MESSAGE_NOT_INITIALIZED }
    }

    internal fun isInitialized(): Boolean {
        return initialized
    }

    private fun initNetworkInfoProvider(context: Context) {
        val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CallbackNetworkInfoProvider()
        } else {
            BroadcastReceiverNetworkInfoProvider()
        }
        provider.register(context)
        networkInfoProvider = provider
    }

    private fun buildOkHttpClient(endpoint: String): OkHttpClient {
        val connectionSpec = when {
            endpoint.startsWith("http://") -> ConnectionSpec.CLEARTEXT
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> ConnectionSpec.RESTRICTED_TLS
            else -> ConnectionSpec.MODERN_TLS
        }
        return OkHttpClient.Builder()
            .addInterceptor(NetworkTimeInterceptor(timeProvider))
            .addInterceptor(GzipRequestInterceptor())
            .callTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionSpecs(listOf(connectionSpec))
            .build()
    }

    private fun setupTheExceptionHandler(appContext: Context?) {
        DatadogExceptionHandler(
            networkInfoProvider,
            timeProvider,
            userInfoProvider,
            logStrategy.getSynchronousWriter(),
            appContext
        ).register()
    }

    private fun setupTheSystemInfoProvider(appContext: Context) {
        // Register Broadcast Receivers
        systemInfoProvider.register(appContext)
    }

    private fun setupLogsUploader(
        endpointUrl: String?
    ) {
        // Start handler to send logs
        val endpoint = endpointUrl ?: DATADOG_US
        val okHttpClient = buildOkHttpClient(endpoint)

        uploader = DataOkHttpUploader(
            endpoint,
            clientToken,
            okHttpClient
        )
        handlerThread =
            DataUploadHandlerThread(
                LOG_UPLOAD_THREAD_NAME,
                logStrategy.getReader(),
                uploader,
                networkInfoProvider,
                systemInfoProvider
            )
        handlerThread.start()
    }

    private fun initSdkCredentials(
        appContext: Context,
        clientToken: String
    ) {
        packageName = appContext.packageName
        packageVersion = appContext.packageManager.getPackageInfo(packageName, 0).let {
            it.versionName ?: it.versionCode.toString()
        }
        contextRef = WeakReference(appContext)
        this.clientToken = clientToken
    }

    private fun setupLifecycleMonitorCallback(appContext: Context) {
        if (appContext is Application) {
            val callback = ProcessLifecycleCallback(networkInfoProvider, appContext)
            appContext.registerActivityLifecycleCallbacks(ProcessLifecycleMonitor(callback))
        }
    }

    internal const val MESSAGE_NOT_INITIALIZED = "Datadog has not been initialized.\n" +
        "Please add the following code in your application's onCreate() method:\n" +
        "Datadog.initialize(context, \"<CLIENT_TOKEN>\");"

    internal const val MESSAGE_DEPRECATED = "%s has been deprecated. " +
        "If you need it, submit an issue at https://github.com/DataDog/dd-sdk-android/issues/"

    // endregion
}
