/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import android.util.Log as AndroidLog
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.triggerUploadWorker
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.user.UserInfoProvider
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
            serviceName = Logger.DEFAULT_SERVICE_NAME,
            level = AndroidLog.ERROR,
            loggerName = LOGGER_NAME,
            message = MESSAGE,
            threadName = thread.name,
            throwable = throwable,
            userInfo = userInfoProvider.getUserInfo(),
            networkInfo = networkInfoProvider?.getLatestNetworkInfo(),
            timestamp = timeProvider.getServerTimestamp(),
            attributes = emptyMap(),
            tags = emptyList()
        )
    }

    // endregion

    companion object {
        private const val LOGGER_NAME = "crash"
        private const val MESSAGE = "Application crash detected"
    }
}
