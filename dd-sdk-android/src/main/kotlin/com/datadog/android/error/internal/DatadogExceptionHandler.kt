/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.triggerUploadWorker
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import java.lang.ref.WeakReference

internal class DatadogExceptionHandler(
    private val networkInfoProvider: NetworkInfoProvider?,
    private val timeProvider: TimeProvider,
    private val userInfoProvider: UserInfoProvider,
    private val writer: Writer<Log>,
    appContext: Context?
) :
    Thread.UncaughtExceptionHandler {

    private val contextRef = WeakReference(appContext)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    // region Thread.UncaughtExceptionHandler

    override fun uncaughtException(t: Thread, e: Throwable) {
        // write the log immediately
        writer.write(createLog(t, e))

        // write a RUM Error too
        (GlobalRum.get() as? AdvancedRumMonitor)?.addCrash(MESSAGE, RumErrorSource.SOURCE, e)

        // trigger a task to send the logs ASAP
        contextRef.get()?.let {
            triggerUploadWorker(it)
        }

        // Always do this one last; this will shut down the VM
        previousHandler?.uncaughtException(t, e)
    }

    // endregion

    // region DatadogExceptionHandler

    fun register() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    // endregion

    // region Internal

    private fun createLog(thread: Thread, throwable: Throwable): Log {
        return Log(
            serviceName = CoreFeature.serviceName,
            level = Log.CRASH,
            loggerName = LOGGER_NAME,
            message = MESSAGE,
            threadName = thread.name,
            throwable = throwable,
            userInfo = userInfoProvider.getUserInfo(),
            networkInfo = networkInfoProvider?.getLatestNetworkInfo(),
            timestamp = timeProvider.getServerTimestamp(),
            attributes = emptyMap(),
            tags = if (CrashReportsFeature.envTag.isEmpty()) {
                emptyList()
            } else {
                listOf(CrashReportsFeature.envTag)
            }
        )
    }

    // endregion

    companion object {
        // If you change these you will have to propagate the changes
        // also into the datadog-native-lib.cpp file inside the dd-sdk-android-ndk module.
        internal const val LOGGER_NAME = "crash"
        internal const val MESSAGE = "Application crash detected"
    }
}
