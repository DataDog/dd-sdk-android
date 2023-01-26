/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import com.datadog.android.Datadog
import com.datadog.android.core.internal.thread.waitToIdle
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.core.internal.utils.isWorkManagerInitialized
import com.datadog.android.core.internal.utils.triggerUploadWorker
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.DatadogCore
import java.lang.ref.WeakReference
import java.util.concurrent.ThreadPoolExecutor

internal class DatadogExceptionHandler(
    private val sdkCore: SdkCore,
    appContext: Context
) : Thread.UncaughtExceptionHandler {

    private val contextRef = WeakReference(appContext)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    // region Thread.UncaughtExceptionHandler

    override fun uncaughtException(t: Thread, e: Throwable) {
        // write the log immediately
        val logsFeature = sdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
        if (logsFeature != null) {
            logsFeature.sendEvent(
                mapOf(
                    "threadName" to t.name,
                    "throwable" to e,
                    "timestamp" to System.currentTimeMillis(),
                    "message" to createCrashMessage(e),
                    "type" to "jvm_crash",
                    "loggerName" to LOGGER_NAME
                )
            )
        } else {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                MISSING_LOGS_FEATURE_INFO
            )
        }

        // write a RUM Error too
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        if (rumFeature != null) {
            rumFeature.sendEvent(
                mapOf(
                    "type" to "jvm_crash",
                    "throwable" to e,
                    "message" to createCrashMessage(e)
                )
            )
        } else {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                MISSING_RUM_FEATURE_INFO
            )
        }

        // TODO RUMM-0000 If DatadogExceptionHandler goes into dedicated module (module of 1 class
        //  only?), we have to wait for the write in some other way
        // give some time to the persistence executor service to finish its tasks
        val coreFeature = (Datadog.globalSdkCore as? DatadogCore)?.coreFeature
        if (coreFeature != null) {
            val idled = (coreFeature.persistenceExecutorService as? ThreadPoolExecutor)
                ?.waitToIdle(MAX_WAIT_FOR_IDLE_TIME_IN_MS) ?: true
            if (!idled) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    EXECUTOR_NOT_IDLED_WARNING_MESSAGE
                )
            }
        }

        // trigger a task to send the logs ASAP
        contextRef.get()?.let {
            if (isWorkManagerInitialized(it)) {
                triggerUploadWorker(it)
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
