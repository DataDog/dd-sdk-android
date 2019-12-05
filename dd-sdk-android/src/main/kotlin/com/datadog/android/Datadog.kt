/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android

import android.content.Context
import com.datadog.android.log.EndpointUpdateStrategy
import com.datadog.android.log.internal.LogHandlerThread
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.file.LogFileStrategy
import com.datadog.android.log.internal.net.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.log.internal.net.LogOkHttpUploader
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.log.internal.net.NetworkInfoProvider
import com.datadog.android.log.internal.utils.sdkLogger
import java.lang.ref.WeakReference

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

    private var initialized: Boolean = false
    private lateinit var clientToken: String
    private lateinit var logStrategy: LogStrategy
    private lateinit var networkInfoProvider: BroadcastReceiverNetworkInfoProvider
    private lateinit var handlerThread: LogHandlerThread
    private lateinit var contextRef: WeakReference<Context>
    private lateinit var uploader: LogUploader
    private var endpointUrl: String = DATADOG_US

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
        check(!initialized) { "Datadog has already been initialized." }

        val appContext = context.applicationContext
        contextRef = WeakReference(appContext)
        this.clientToken = clientToken
        logStrategy = LogFileStrategy(appContext)

        // Start handler to send logs
        uploader = LogOkHttpUploader(endpointUrl ?: DATADOG_US, Datadog.clientToken)
        handlerThread = LogHandlerThread(logStrategy.getLogReader(), uploader)
        handlerThread.start()

        // Register Broadcast Receiver
        // TODO RUMM-44 implement a provider using ConnectivityManager.registerNetworkCallback
        val broadcastReceiver = BroadcastReceiverNetworkInfoProvider().apply {
            register(appContext)
        }

        networkInfoProvider = broadcastReceiver

        initialized = true
    }

    /**
     * Changes the endpoint to which data is sent.
     * @param endpointUrl the endpoint url to target, or null to use the default.
     * Possible values are [DATADOG_US], [DATADOG_EU] or a custom endpoint.
     * @param strategy the strategy defining how to handle logs created previously.
     * Because logs are sent asynchronously, some logs intended for the previous endpoint
     * might still be yet to sent.
     */
    fun setEndpointUrl(endpointUrl: String, strategy: EndpointUpdateStrategy) {
        when (strategy) {
            EndpointUpdateStrategy.DISCARD_OLD_LOGS -> {
                logStrategy.getLogReader().dropAllBatches()
                sdkLogger.w(
                    "$TAG: old logs targeted at $endpointUrl " +
                        "will now be deleted"
                )
            }
            EndpointUpdateStrategy.SEND_OLD_LOGS_TO_NEW_ENDPOINT -> {
                sdkLogger.w(
                    "$TAG: old logs targeted at $endpointUrl " +
                        "will now be sent to $endpointUrl"
                )
            }
        }
        uploader.setEndpoint(endpointUrl)
    }

    /**
     * Stop all Datadog work.
     */
    fun stop() {
        checkInitialized()
        handlerThread.quitSafely()
        contextRef.get()?.unregisterReceiver(networkInfoProvider)
        contextRef.clear()
        initialized = false
    }

    // region Internal Provider

    internal fun getLogStrategy(): LogStrategy {
        checkInitialized()
        return logStrategy
    }

    internal fun getNetworkInfoProvider(): NetworkInfoProvider {
        return networkInfoProvider
    }

    // endregion

    // region Internal

    @Suppress("CheckInternal")
    private fun checkInitialized() {
        check(initialized) {
            "Datadog has not been initialized.\n" +
                "Please add the following code in your application's onCreate() method:\n" +
                "Datadog.initialized(context, \"CLIENT_TOKEN\");"
        }
    }

    // endregion
}
