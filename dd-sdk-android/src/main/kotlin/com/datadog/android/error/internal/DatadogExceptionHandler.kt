/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import com.datadog.android.Datadog
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.thread.waitToIdle
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.triggerUploadWorker
import com.datadog.android.log.internal.domain.DatadogLogGenerator
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.v2.core.DatadogCore
import java.lang.ref.WeakReference
import java.util.concurrent.ThreadPoolExecutor

internal class DatadogExceptionHandler(
    private val logGenerator: DatadogLogGenerator,
    private val writer: DataWriter<LogEvent>,
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
        (GlobalRum.get() as? AdvancedRumMonitor)?.addCrash(
            createCrashMessage(e),
            RumErrorSource.SOURCE,
            e
        )

        // give some time to the persistence executor service to finish its tasks
        val coreFeature = (Datadog.globalSDKCore as? DatadogCore)?.coreFeature
        if (coreFeature != null) {
            val idled = (coreFeature.persistenceExecutorService as? ThreadPoolExecutor)
                ?.waitToIdle(MAX_WAIT_FOR_IDLE_TIME_IN_MS) ?: true
            if (!idled) {
                devLogger.w(EXECUTOR_NOT_IDLED_WARNING_MESSAGE)
            }
        }

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

    private fun createLog(thread: Thread, throwable: Throwable): LogEvent {
        return logGenerator.generateLog(
            DatadogLogGenerator.CRASH,
            createCrashMessage(throwable),
            throwable,
            emptyMap(),
            emptySet(),
            System.currentTimeMillis(),
            thread.name
        )
    }

    private fun createCrashMessage(throwable: Throwable): String {
        val rawMessage = throwable.message
        return if (rawMessage.isNullOrBlank()) {
            val className = throwable.javaClass.canonicalName ?: throwable.javaClass.simpleName
            "$MESSAGE: $className"
        } else {
            rawMessage
        }
    }

    // endregion

    companion object {
        // If you change these you will have to propagate the changes
        // also into the datadog-native-lib.cpp file inside the dd-sdk-android-ndk module.
        internal const val LOGGER_NAME = CrashReportsFeature.CRASH_FEATURE_NAME
        internal const val MESSAGE = "Application crash detected"
        internal const val MAX_WAIT_FOR_IDLE_TIME_IN_MS = 100L
        internal const val EXECUTOR_NOT_IDLED_WARNING_MESSAGE =
            "Datadog SDK is in an unexpected state due to an ongoing crash. " +
                "Some events could be lost"
    }
}
