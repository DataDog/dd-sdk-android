/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.readTextSafe
import com.datadog.android.core.internal.utils.join
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.log.LogAttributes
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.UserInfo
import com.google.gson.JsonObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService

@Suppress("TooManyFunctions", "LongParameterList")
internal class DatadogNdkCrashHandler(
    storageDir: File,
    private val dataPersistenceExecutorService: ExecutorService,
    private val ndkCrashLogDeserializer: Deserializer<String, NdkCrashLog>,
    private val rumEventDeserializer: Deserializer<String, JsonObject>,
    private val networkInfoDeserializer: Deserializer<String, NetworkInfo>,
    private val userInfoDeserializer: Deserializer<String, UserInfo>,
    private val internalLogger: InternalLogger,
    private val rumFileReader: BatchFileReader,
    private val envFileReader: FileReader
) : NdkCrashHandler {

    internal val ndkCrashDataDirectory: File = getNdkGrantedDir(storageDir)

    internal var lastRumViewEvent: JsonObject? = null
    internal var lastUserInfo: UserInfo? = null
    internal var lastNetworkInfo: NetworkInfo? = null
    internal var lastNdkCrashLog: NdkCrashLog? = null

    internal var processedForLogs = false
    internal var processedForRum = false

    // region NdkCrashHandler

    override fun prepareData() {
        dataPersistenceExecutorService.submitSafe("NDK crash check", internalLogger) {
            @Suppress("ThreadSafety")
            readCrashData()
        }
    }

    override fun handleNdkCrash(
        sdkCore: FeatureSdkCore,
        reportTarget: NdkCrashHandler.ReportTarget
    ) {
        dataPersistenceExecutorService.submitSafe("NDK crash report ", internalLogger) {
            @Suppress("ThreadSafety")
            checkAndHandleNdkCrashReport(sdkCore, reportTarget)
        }
    }

    // endregion

    // region Internal

    @Suppress("NestedBlockDepth")
    @WorkerThread
    private fun readCrashData() {
        if (!ndkCrashDataDirectory.existsSafe(internalLogger)) {
            return
        }
        try {
            ndkCrashDataDirectory.listFilesSafe(internalLogger)?.forEach { file ->
                when (file.name) {
                    // TODO RUMM-1944 Data from NDK should be also encrypted
                    CRASH_DATA_FILE_NAME ->
                        lastNdkCrashLog =
                            file.readTextSafe(internalLogger = internalLogger)?.let {
                                ndkCrashLogDeserializer.deserialize(it)
                            }

                    RUM_VIEW_EVENT_FILE_NAME ->
                        lastRumViewEvent =
                            readRumFileContent(
                                file,
                                rumFileReader
                            )?.let { rumEventDeserializer.deserialize(it) }

                    USER_INFO_FILE_NAME ->
                        lastUserInfo =
                            readFileContent(
                                file,
                                envFileReader
                            )?.let { userInfoDeserializer.deserialize(it) }

                    NETWORK_INFO_FILE_NAME ->
                        lastNetworkInfo =
                            readFileContent(
                                file,
                                envFileReader
                            )?.let { networkInfoDeserializer.deserialize(it) }
                }
            }
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_READ_NDK_DIR },
                e
            )
        } finally {
            clearCrashLog()
        }
    }

    @WorkerThread
    private fun readFileContent(file: File, fileReader: FileReader): String? {
        val content = fileReader.readData(file)
        return if (content.isEmpty()) {
            null
        } else {
            String(content)
        }
    }

    @WorkerThread
    private fun readRumFileContent(file: File, fileReader: BatchFileReader): String? {
        val content = fileReader.readData(file)
        return if (content.isEmpty()) {
            null
        } else {
            String(content.join(ByteArray(0), internalLogger = internalLogger))
        }
    }

    @WorkerThread
    private fun checkAndHandleNdkCrashReport(
        sdkCore: FeatureSdkCore,
        reportTarget: NdkCrashHandler.ReportTarget
    ) {
        if (lastNdkCrashLog != null) {
            handleNdkCrashLog(
                sdkCore,
                lastNdkCrashLog,
                lastRumViewEvent,
                lastUserInfo,
                lastNetworkInfo,
                reportTarget
            )
        }

        when (reportTarget) {
            NdkCrashHandler.ReportTarget.RUM -> processedForRum = true
            NdkCrashHandler.ReportTarget.LOGS -> processedForLogs = true
        }

        if (processedForRum && processedForLogs) {
            clearAllReferences()
        }
    }

    private fun clearAllReferences() {
        lastRumViewEvent = null
        lastNetworkInfo = null
        lastUserInfo = null
        lastNdkCrashLog = null
    }

    @WorkerThread
    private fun handleNdkCrashLog(
        sdkCore: FeatureSdkCore,
        ndkCrashLog: NdkCrashLog?,
        lastViewEvent: JsonObject?,
        lastUserInfo: UserInfo?,
        lastNetworkInfo: NetworkInfo?,
        reportTarget: NdkCrashHandler.ReportTarget
    ) {
        if (ndkCrashLog == null) {
            return
        }
        val errorLogMessage = LOG_CRASH_MSG.format(Locale.US, ndkCrashLog.signalName)

        when (reportTarget) {
            NdkCrashHandler.ReportTarget.RUM -> {
                if (lastViewEvent != null) {
                    sendCrashRumEvent(
                        sdkCore,
                        errorLogMessage,
                        ndkCrashLog,
                        lastViewEvent
                    )
                }
            }

            NdkCrashHandler.ReportTarget.LOGS -> {
                sendCrashLogEvent(
                    sdkCore,
                    errorLogMessage,
                    generateLogAttributes(lastViewEvent, ndkCrashLog),
                    ndkCrashLog,
                    lastNetworkInfo,
                    lastUserInfo
                )
            }
        }
    }

    private fun generateLogAttributes(
        lastRumViewEvent: JsonObject?,
        ndkCrashLog: NdkCrashLog
    ): Map<String, String> {
        val logAttributes = if (lastRumViewEvent != null) {
            val (applicationId, sessionId, viewId) = try {
                val extractId = { property: String ->
                    lastRumViewEvent.getAsJsonObject(property)
                        .getAsJsonPrimitive("id")
                        .asString
                }
                val applicationId = extractId("application")
                val sessionId = extractId("session")
                val viewId = extractId("view")
                Triple(applicationId, sessionId, viewId)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { WARN_CANNOT_READ_VIEW_INFO_DATA },
                    e
                )
                Triple(null, null, null)
            }
            if (applicationId != null && sessionId != null && viewId != null) {
                mapOf(
                    LogAttributes.RUM_SESSION_ID to sessionId,
                    LogAttributes.RUM_APPLICATION_ID to applicationId,
                    LogAttributes.RUM_VIEW_ID to viewId,
                    LogAttributes.ERROR_STACK to ndkCrashLog.stacktrace
                )
            } else {
                mapOf(
                    LogAttributes.ERROR_STACK to ndkCrashLog.stacktrace
                )
            }
        } else {
            mapOf(
                LogAttributes.ERROR_STACK to ndkCrashLog.stacktrace
            )
        }
        return logAttributes
    }

    @Suppress("StringLiteralDuplication")
    @WorkerThread
    private fun sendCrashRumEvent(
        sdkCore: FeatureSdkCore,
        errorLogMessage: String,
        ndkCrashLog: NdkCrashLog,
        lastViewEvent: JsonObject
    ) {
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        if (rumFeature != null) {
            rumFeature.sendEvent(
                mapOf(
                    "type" to "ndk_crash",
                    "timestamp" to ndkCrashLog.timestamp,
                    "signalName" to ndkCrashLog.signalName,
                    "stacktrace" to ndkCrashLog.stacktrace,
                    "message" to errorLogMessage,
                    "lastViewEvent" to lastViewEvent
                )
            )
        } else {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { INFO_RUM_FEATURE_NOT_REGISTERED }
            )
        }
    }

    @Suppress("StringLiteralDuplication")
    @WorkerThread
    private fun sendCrashLogEvent(
        sdkCore: FeatureSdkCore,
        errorLogMessage: String,
        logAttributes: Map<String, String>,
        ndkCrashLog: NdkCrashLog,
        lastNetworkInfo: NetworkInfo?,
        lastUserInfo: UserInfo?
    ) {
        val logsFeature = sdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
        if (logsFeature != null) {
            logsFeature.sendEvent(
                mapOf(
                    "loggerName" to LOGGER_NAME,
                    "type" to "ndk_crash",
                    "message" to errorLogMessage,
                    "attributes" to logAttributes,
                    "timestamp" to ndkCrashLog.timestamp,
                    "networkInfo" to lastNetworkInfo,
                    "userInfo" to lastUserInfo
                )
            )
        } else {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { INFO_LOGS_FEATURE_NOT_REGISTERED }
            )
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun clearCrashLog() {
        if (ndkCrashDataDirectory.existsSafe(internalLogger)) {
            try {
                ndkCrashDataDirectory.listFilesSafe(internalLogger)
                    ?.forEach { it.deleteRecursively() }
            } catch (e: Throwable) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    {
                        "Unable to clear the NDK crash report file:" +
                            " ${ndkCrashDataDirectory.absolutePath}"
                    },
                    e
                )
            }
        }
    }

    // endregion

    companion object {

        internal const val RUM_VIEW_EVENT_FILE_NAME = "last_view_event"
        internal const val CRASH_DATA_FILE_NAME = "crash_log"
        internal const val USER_INFO_FILE_NAME = "user_information"
        internal const val NETWORK_INFO_FILE_NAME = "network_information"

        internal const val LOGGER_NAME = "ndk_crash"

        internal const val LOG_CRASH_MSG = "NDK crash detected with signal: %s"
        internal const val ERROR_READ_NDK_DIR = "Error while trying to read the NDK crash directory"

        internal const val WARN_CANNOT_READ_VIEW_INFO_DATA =
            "Cannot read application, session, view IDs data from view event."

        internal const val INFO_LOGS_FEATURE_NOT_REGISTERED =
            "Logs feature is not registered, won't report NDK crash info as log."
        internal const val INFO_RUM_FEATURE_NOT_REGISTERED =
            "RUM feature is not registered, won't report NDK crash info as RUM error."

        private const val STORAGE_VERSION = 2

        internal const val NDK_CRASH_REPORTS_FOLDER_NAME = "ndk_crash_reports_v$STORAGE_VERSION"
        private const val NDK_CRASH_REPORTS_PENDING_FOLDER_NAME =
            "ndk_crash_reports_intermediary_v$STORAGE_VERSION"

        private fun getNdkGrantedDir(storageDir: File): File {
            return File(storageDir, NDK_CRASH_REPORTS_FOLDER_NAME)
        }

        private fun getNdkPendingDir(storageDir: File): File {
            return File(storageDir, NDK_CRASH_REPORTS_PENDING_FOLDER_NAME)
        }

        internal fun getLastViewEventFile(storageDir: File): File {
            return File(getNdkGrantedDir(storageDir), RUM_VIEW_EVENT_FILE_NAME)
        }

        internal fun getPendingNetworkInfoFile(storageDir: File): File {
            return File(getNdkPendingDir(storageDir), NETWORK_INFO_FILE_NAME)
        }

        internal fun getGrantedNetworkInfoFile(storageDir: File): File {
            return File(getNdkGrantedDir(storageDir), NETWORK_INFO_FILE_NAME)
        }

        internal fun getPendingUserInfoFile(storageDir: File): File {
            return File(getNdkPendingDir(storageDir), USER_INFO_FILE_NAME)
        }

        internal fun getGrantedUserInfoFile(storageDir: File): File {
            return File(getNdkGrantedDir(storageDir), USER_INFO_FILE_NAME)
        }
    }
}
