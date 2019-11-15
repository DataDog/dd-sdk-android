/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android

import android.content.Context
import com.datadog.android.log.internal.LogHandlerThread
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.file.LogFileStrategy
import com.datadog.android.log.internal.net.LogOkHttpUploader
import com.datadog.android.log.internal.net.LogUploader

/**
 * This class initializes the Datadog SDK, and sets up communication with the server.
 */
object Datadog {

    private var initialized: Boolean = false

    private lateinit var clientToken: String
    private lateinit var logStrategy: LogStrategy

    var endpointBaseUrl: String = "https://browser-http-intake.logs.datadoghq.com"

    private val handlerThread = LogHandlerThread()

    /**
     * Initializes the Datadog SDK.
     * @param context your application context
     * @param clientToken your API key of type Client Token
     */
    fun initialize(context: Context, clientToken: String) {
        check(!initialized) {
            "Datadog has already been initialized."
        }

        // this.contextRef = WeakReference(context.applicationContext)
        this.clientToken = clientToken
        logStrategy = LogFileStrategy(context.applicationContext)

        handlerThread.start()

        initialized = true
    }

    // region Internal Provider

    internal fun getLogStrategy(): LogStrategy {
        checkInitialized()
        return logStrategy
    }

    internal fun getLogUploader(): LogUploader {
        checkInitialized()
        return LogOkHttpUploader(endpointBaseUrl, clientToken)
    }

    // endregion

    // region Internal

    private fun checkInitialized() {
        check(initialized) {
            "Datadog has not been initialized.\n" +
                "Please add the following code in your application's onCreate() method:\n" +
                "Datadog.initialized(context, \"CLIENT_TOKEN\");"
        }
    }

    // endregion
}
