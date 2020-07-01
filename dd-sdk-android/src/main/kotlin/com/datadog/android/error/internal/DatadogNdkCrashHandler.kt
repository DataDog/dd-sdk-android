/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogSerializer
import java.io.File

internal class DatadogNdkCrashHandler : NdkCrashHandler {

    init {
        System.loadLibrary("datadog-native-lib")
    }

    override fun register(appContext: Context) {
        val ndkCrashesDirs =
            File(
                appContext.filesDir.absolutePath +
                    File.separator +
                    CrashLogFileStrategy.CRASH_REPORTS_FOLDER
            )
        registerSignalHandler(
            ndkCrashesDirs.absolutePath,
            CoreFeature.serviceName,
            DatadogExceptionHandler.LOGGER_NAME,
            DatadogExceptionHandler.MESSAGE,
            LogSerializer.resolveLogLevelStatus(Log.CRASH),
            CrashReportsFeature.envName
        )
    }

    override fun unregister() {
        unregisterSignalHandler()
    }

    // region NDK

    /**
     * Initialize native signal handler to catch native crashes.
     */
    @SuppressWarnings("LongParameterList")
    external fun registerSignalHandler(
        storagePath: String,
        serviceName: String,
        loggerName: String,
        genericLogMessage: String,
        emergencyStatus: String,
        environment: String
    )

    /**
     * Deinitialzie native signal handler to leave native crashes alone.
     */
    external fun unregisterSignalHandler()

    // endregion
}
