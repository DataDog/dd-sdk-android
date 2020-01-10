/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android

import android.content.Context
import android.os.Build
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.GzipRequestInterceptor
import com.datadog.android.core.internal.net.NetworkTimeInterceptor
import com.datadog.android.core.internal.time.DatadogTimeProvider
import com.datadog.android.core.internal.time.MutableTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.error.internal.DatadogExceptionHandler
import com.datadog.android.log.EndpointUpdateStrategy
import com.datadog.android.log.internal.LogHandlerThread
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogFileStrategy
import com.datadog.android.log.internal.net.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.log.internal.net.CallbackNetworkInfoProvider
import com.datadog.android.log.internal.net.LogOkHttpUploader
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.log.internal.net.NetworkInfoProvider
import com.datadog.android.log.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.log.internal.user.DatadogUserInfoProvider
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.log.internal.utils.devLogger
import java.lang.IllegalStateException
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * This class initializes the Datadog SDK, and sets up communication with the server.
 */
object Datadog {

    /**
     * The endpoint for our US based servers, used by default by the SDK.
     * @see [initialize]
     */
    @Suppress("MemberVisibilityCanBePrivate")
    const val DATADOG_US = "https://mobile-http-intake.logs.datadoghq.com"

    /**
     * The endpoint for our Europe based servers.
     * Use this in your call to [initialize] if you log on
     * [app.datadoghq.eu](https://app.datadoghq.eu/) instead of
     * [app.datadoghq.com](https://app.datadoghq.com/)
     */
    @Suppress("MemberVisibilityCanBePrivate")
    const val DATADOG_EU = "https://mobile-http-intake.logs.datadoghq.eu"

    private const val TAG = "Datadog"

    internal const val NETWORK_TIMEOUT_MS = DatadogTimeProvider.MAX_OFFSET_DEVIATION / 2

    private var initialized: Boolean = false
    private lateinit var clientToken: String
    private lateinit var logStrategy: PersistenceStrategy<Log>
    private lateinit var networkInfoProvider: NetworkInfoProvider
    private lateinit var handlerThread: LogHandlerThread
    private lateinit var contextRef: WeakReference<Context>
    private lateinit var uploader: LogUploader
    private lateinit var timeProvider: MutableTimeProvider
    private lateinit var userInfoProvider: MutableUserInfoProvider

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
        packageName = context.packageName
        packageVersion = context.packageManager.getPackageInfo(packageName, 0).let {
            it.versionName ?: it.versionCode.toString()
        }
        contextRef = WeakReference(appContext)
        this.clientToken = clientToken
        logStrategy =
            LogFileStrategy(appContext)

        // prepare time management
        timeProvider = DatadogTimeProvider(appContext)

        // Prepare user info management
        userInfoProvider = DatadogUserInfoProvider()

        // Register Broadcast Receivers
        initializeNetworkInfoProvider(appContext)
        val systemBroadcastReceiver = BroadcastReceiverSystemInfoProvider().apply {
            register(appContext)
        }

        // Start handler to send logs
        val endpoint = endpointUrl ?: DATADOG_US
        val okHttpClient = buildOkHttpClient(endpoint)

        uploader = LogOkHttpUploader(endpoint, Datadog.clientToken, okHttpClient)
        handlerThread = LogHandlerThread(
            logStrategy.getReader(),
            uploader,
            networkInfoProvider,
            systemBroadcastReceiver
        )
        handlerThread.start()

        initialized = true

        // Error Management (set it up last. Until this is done, we don't know)
        DatadogExceptionHandler(
            networkInfoProvider,
            timeProvider,
            userInfoProvider,
            logStrategy.getSynchronousWriter(),
            appContext
        ).register()
    }

    /**
     * Changes the endpoint to which data is sent.
     * @param endpointUrl the endpoint url to target, or null to use the default.
     * Possible values are [DATADOG_US], [DATADOG_EU] or a custom endpoint.
     * @param strategy the strategy defining how to handle logs created previously.
     * Because logs are sent asynchronously, some logs intended for the previous endpoint
     * might still be yet to sent.
     */
    @JvmStatic
    fun setEndpointUrl(endpointUrl: String, strategy: EndpointUpdateStrategy) {
        when (strategy) {
            EndpointUpdateStrategy.DISCARD_OLD_LOGS -> {
                logStrategy.getReader().dropAllBatches()
                devLogger.w(
                    "$TAG: old logs targeted at $endpointUrl " +
                        "will now be deleted"
                )
            }
            EndpointUpdateStrategy.SEND_OLD_LOGS_TO_NEW_ENDPOINT -> {
                devLogger.w(
                    "$TAG: old logs targeted at $endpointUrl " +
                        "will now be sent to $endpointUrl"
                )
            }
        }
        uploader.setEndpoint(endpointUrl)
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

    // region Internal Provider

    internal fun getLogStrategy(): PersistenceStrategy<Log> {
        checkInitialized()
        return logStrategy
    }

    internal fun getLogUploader(): LogUploader {
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

    // endregion

    // region Internal Initialization

    @Suppress("CheckInternal")
    private fun checkInitialized() {
        check(initialized) {
            "Datadog has not been initialized.\n" +
                "Please add the following code in your application's onCreate() method:\n" +
                "Datadog.initialize(context, \"<CLIENT_TOKEN>\");"
        }
    }

    private fun initializeNetworkInfoProvider(context: Context) {
        val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CallbackNetworkInfoProvider()
        } else {
            BroadcastReceiverNetworkInfoProvider()
        }
        provider.register(context)
        networkInfoProvider = provider
    }

    private fun buildOkHttpClient(endpoint: String): OkHttpClient {
        val connectionSpec = if (endpoint.startsWith("http://")) {
            ConnectionSpec.CLEARTEXT
        } else {
            ConnectionSpec.RESTRICTED_TLS
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

    // endregion
}
