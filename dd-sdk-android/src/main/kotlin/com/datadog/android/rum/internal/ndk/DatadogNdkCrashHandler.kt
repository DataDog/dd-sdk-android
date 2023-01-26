/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.readTextSafe
import com.datadog.android.core.internal.utils.join
import com.datadog.android.log.LogAttributes
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.UserInfo
import com.google.gson.JsonObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

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

    internal var lastSerializedRumViewEvent: String? = null
    internal var lastSerializedUserInformation: String? = null
    internal var lastSerializedNdkCrashLog: String? = null
    internal var lastSerializedNetworkInformation: String? = null

    // region NdkCrashHandler

    override fun prepareData() {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            dataPersistenceExecutorService.submit {
                @Suppress("ThreadSafety")
                readCrashData()
            }
        } catch (e: RejectedExecutionException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                ERROR_TASK_REJECTED,
                e
            )
        }
    }

    override fun handleNdkCrash(sdkCore: SdkCore) {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            dataPersistenceExecutorService.submit {
                @Suppress("ThreadSafety")
                checkAndHandleNdkCrashReport(sdkCore)
            }
        } catch (e: RejectedExecutionException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                ERROR_TASK_REJECTED,
                e
            )
        }
    }

    // endregion

    // region Internal

    @WorkerThread
    private fun readCrashData() {
        if (!ndkCrashDataDirectory.existsSafe()) {
            return
        }
        try {
            ndkCrashDataDirectory.listFilesSafe()?.forEach {
                when (it.name) {
                    // TODO RUMM-1944 Data from NDK should be also encrypted
                    CRASH_DATA_FILE_NAME -> lastSerializedNdkCrashLog = it.readTextSafe()
                    RUM_VIEW_EVENT_FILE_NAME ->
                        lastSerializedRumViewEvent =
                            readRumFileContent(it, rumFileReader)
                    USER_INFO_FILE_NAME ->
                        lastSerializedUserInformation =
                            readFileContent(it, envFileReader)
                    NETWORK_INFO_FILE_NAME ->
                        lastSerializedNetworkInformation =
                            readFileContent(it, envFileReader)
                }
            }
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                ERROR_READ_NDK_DIR,
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
            String(content.join(ByteArray(0)))
        }
    }

    @WorkerThread
    private fun checkAndHandleNdkCrashReport(sdkCore: SdkCore) {
        val lastSerializedRumViewEvent = lastSerializedRumViewEvent
        val lastSerializedUserInformation: String? = lastSerializedUserInformation
        val lastSerializedNdkCrashLog: String? = lastSerializedNdkCrashLog
        val lastSerializedNetworkInformation: String? = lastSerializedNetworkInformation
        if (lastSerializedNdkCrashLog != null) {
            val lastNdkCrashLog = ndkCrashLogDeserializer.deserialize(lastSerializedNdkCrashLog)
            val lastRumViewEvent = lastSerializedRumViewEvent?.let {
                rumEventDeserializer.deserialize(it)
            }
            val lastUserInfo = lastSerializedUserInformation?.let {
                userInfoDeserializer.deserialize(it)
            }
            val lastNetworkInfo = lastSerializedNetworkInformation?.let {
                networkInfoDeserializer.deserialize(it)
            }
            handleNdkCrashLog(
                sdkCore,
                lastNdkCrashLog,
                lastRumViewEvent,
                lastUserInfo,
                lastNetworkInfo
            )
        }
        clearAllReferences()
    }

    private fun clearAllReferences() {
        lastSerializedNdkCrashLog = null
        lastSerializedNetworkInformation = null
        lastSerializedRumViewEvent = null
        lastSerializedUserInformation = null
    }

    @WorkerThread
    private fun handleNdkCrashLog(
        sdkCore: SdkCore,
        ndkCrashLog: NdkCrashLog?,
        lastViewEvent: JsonObject?,
        lastUserInfo: UserInfo?,
        lastNetworkInfo: NetworkInfo?
    ) {
        if (ndkCrashLog == null) {
            return
        }
        val errorLogMessage = LOG_CRASH_MSG.format(Locale.US, ndkCrashLog.signalName)
        val logAttributes: Map<String, String>
        if (lastViewEvent != null) {
            val (applicationId, sessionId, viewId) = try {
                val extractId = { property: String ->
                    lastViewEvent.getAsJsonObject(property)
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
                    WARN_CANNOT_READ_VIEW_INFO_DATA,
                    e
                )
                Triple(null, null, null)
            }
            logAttributes = if (applicationId != null && sessionId != null && viewId != null) {
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
            updateViewEventAndSendError(
                sdkCore,
                errorLogMessage,
                ndkCrashLog,
                lastViewEvent
            )
        } else {
            logAttributes = mapOf(
                LogAttributes.ERROR_STACK to ndkCrashLog.stacktrace
            )
        }

        sendCrashLogEvent(
            sdkCore,
            errorLogMessage,
            logAttributes,
            ndkCrashLog,
            lastNetworkInfo,
            lastUserInfo
        )
    }

    @Suppress("StringLiteralDuplication")
    @WorkerThread
    private fun updateViewEventAndSendError(
        sdkCore: SdkCore,
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
                INFO_RUM_FEATURE_NOT_REGISTERED
            )
        }
    }

    @Suppress("StringLiteralDuplication")
    @WorkerThread
    private fun sendCrashLogEvent(
        sdkCore: SdkCore,
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
                INFO_LOGS_FEATURE_NOT_REGISTERED
            )
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun clearCrashLog() {
        if (ndkCrashDataDirectory.existsSafe()) {
            try {
                ndkCrashDataDirectory.listFilesSafe()?.forEach { it.deleteRecursively() }
            } catch (e: Throwable) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    targets = listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    "Unable to clear the NDK crash report file:" +
                        " ${ndkCrashDataDirectory.absolutePath}",
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

        internal const val ERROR_TASK_REJECTED = "Unable to schedule operation on the executor"

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
