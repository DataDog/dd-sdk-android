/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import androidx.work.WorkManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.JvmCrash
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.thread.waitToIdle
import com.datadog.android.core.internal.utils.asString
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.internal.utils.triggerUploadWorker
import com.datadog.android.internal.utils.loggableStackTrace
import java.lang.ref.WeakReference
import java.util.concurrent.ThreadPoolExecutor

internal class DatadogExceptionHandler(
    private val sdkCore: FeatureSdkCore,
    appContext: Context
) : Thread.UncaughtExceptionHandler {

    private val contextRef = WeakReference(appContext)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    // region Thread.UncaughtExceptionHandler

    override fun uncaughtException(t: Thread, e: Throwable) {
        val threads = getThreadDumps(t, e)

        // write a RUM Error too
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        if (rumFeature != null) {
            rumFeature.sendEvent(
                JvmCrash.Rum(
                    throwable = e,
                    message = createCrashMessage(e),
                    threads = threads
                )
            )
        } else {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { MISSING_RUM_FEATURE_INFO }
            )
        }

        // TODO RUM-3794 If DatadogExceptionHandler goes into dedicated module (module of 1 class
        //  only?), we have to wait for the write in some other way
        // give some time to the persistence executor service to finish its tasks
        if (sdkCore is InternalSdkCore) {
            val idled = (sdkCore.getPersistenceExecutorService() as? ThreadPoolExecutor)
                ?.waitToIdle(MAX_WAIT_FOR_IDLE_TIME_IN_MS, sdkCore.internalLogger) ?: true
            if (!idled) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { EXECUTOR_NOT_IDLED_WARNING_MESSAGE }
                )
            }
        }

        // trigger a task to send the logs ASAP
        contextRef.get()?.let {
            if (WorkManager.isInitialized()) {
                triggerUploadWorker(it, sdkCore.name, sdkCore.internalLogger)
            }
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

    private fun createCrashMessage(throwable: Throwable): String {
        val rawMessage = throwable.message
        return if (rawMessage.isNullOrBlank()) {
            val className = throwable.javaClass.canonicalName ?: throwable.javaClass.simpleName
            "$MESSAGE: $className"
        } else {
            rawMessage
        }
    }

    private fun getThreadDumps(crashedThread: Thread, e: Throwable): List<ThreadDump> {
        return mutableListOf(
            ThreadDump(
                crashed = true,
                name = crashedThread.name,
                state = crashedThread.state.asString(),
                stack = e.loggableStackTrace()
            )
        ) + safeGetAllStacktraces()
            .filterKeys { it != crashedThread }
            .filterValues { it.isNotEmpty() }
            .map { (thread, stackTrace) ->
                ThreadDump(
                    name = thread.name,
                    state = thread.state.asString(),
                    stack = stackTrace.loggableStackTrace(),
                    crashed = false
                )
            }
    }

    private fun safeGetAllStacktraces(): Map<Thread, Array<StackTraceElement>> {
        return try {
            Thread.getAllStackTraces()
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            // coroutines machinery can throw errors here
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Failed to get all threads dump" },
                t
            )
            emptyMap()
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
                "Some events could be lost."
        internal const val MISSING_LOGS_FEATURE_INFO =
            "Logs feature is not registered, won't report crash as log."
        internal const val MISSING_RUM_FEATURE_INFO =
            "RUM feature is not registered, won't report crash as RUM event."
    }
}
