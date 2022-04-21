/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import android.content.Context
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.readTextSafe
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.join
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.utils.errorWithTelemetry
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

internal class DatadogNdkCrashHandler(
    appContext: Context,
    private val dataPersistenceExecutorService: ExecutorService,
    internal val logGenerator: LogGenerator,
    private val ndkCrashLogDeserializer: Deserializer<NdkCrashLog>,
    private val rumEventDeserializer: Deserializer<Any>,
    private val networkInfoDeserializer: Deserializer<NetworkInfo>,
    private val userInfoDeserializer: Deserializer<UserInfo>,
    private val internalLogger: Logger,
    private val timeProvider: TimeProvider,
    private val fileHandler: FileHandler,
    private val rumEventSourceProvider: RumEventSourceProvider =
        RumEventSourceProvider(CoreFeature.sourceName)
) : NdkCrashHandler {

    private val ndkCrashDataDirectory: File = getNdkGrantedDir(appContext)

    internal var lastSerializedRumViewEvent: String? = null
    internal var lastSerializedUserInformation: String? = null
    internal var lastSerializedNdkCrashLog: String? = null
    internal var lastSerializedNetworkInformation: String? = null

    // region NdkCrashHandler

    override fun prepareData() {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            dataPersistenceExecutorService.submit {
                readCrashData()
            }
        } catch (e: RejectedExecutionException) {
            internalLogger.errorWithTelemetry(ERROR_TASK_REJECTED, e)
        }
    }

    override fun handleNdkCrash(
        logWriter: DataWriter<LogEvent>,
        rumWriter: DataWriter<Any>
    ) {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            dataPersistenceExecutorService.submit {
                checkAndHandleNdkCrashReport(logWriter, rumWriter)
            }
        } catch (e: RejectedExecutionException) {
            internalLogger.errorWithTelemetry(ERROR_TASK_REJECTED, e)
        }
    }

    // endregion

    // region Internal

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
                            readFileContent(it, fileHandler)
                    USER_INFO_FILE_NAME ->
                        lastSerializedUserInformation =
                            readFileContent(it, fileHandler)
                    NETWORK_INFO_FILE_NAME ->
                        lastSerializedNetworkInformation =
                            readFileContent(it, fileHandler)
                }
            }
        } catch (e: SecurityException) {
            internalLogger.errorWithTelemetry(ERROR_READ_NDK_DIR, e)
        } finally {
            clearCrashLog()
        }
    }

    private fun readFileContent(file: File, fileHandler: FileHandler): String? {
        val content = fileHandler.readData(file)
        return if (content.isEmpty()) {
            null
        } else {
            String(content.join(ByteArray(0)))
        }
    }

    private fun checkAndHandleNdkCrashReport(
        logWriter: DataWriter<LogEvent>,
        rumWriter: DataWriter<Any>
    ) {
        val lastSerializedRumViewEvent = lastSerializedRumViewEvent
        val lastSerializedUserInformation: String? = lastSerializedUserInformation
        val lastSerializedNdkCrashLog: String? = lastSerializedNdkCrashLog
        val lastSerializedNetworkInformation: String? = lastSerializedNetworkInformation
        if (lastSerializedNdkCrashLog != null) {
            val lastNdkCrashLog = ndkCrashLogDeserializer.deserialize(lastSerializedNdkCrashLog)
            val lastRumViewEvent = lastSerializedRumViewEvent?.let {
                rumEventDeserializer.deserialize(it) as? ViewEvent
            }
            val lastUserInfo = lastSerializedUserInformation?.let {
                userInfoDeserializer.deserialize(it)
            }
            val lastNetworkInfo = lastSerializedNetworkInformation?.let {
                networkInfoDeserializer.deserialize(it)
            }
            handleNdkCrashLog(
                logWriter,
                rumWriter,
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

    @SuppressWarnings("LongParameterList")
    private fun handleNdkCrashLog(
        logWriter: DataWriter<LogEvent>,
        rumWriter: DataWriter<Any>,
        ndkCrashLog: NdkCrashLog?,
        lastViewEvent: ViewEvent?,
        lastUserInfo: UserInfo?,
        lastNetworkInfo: NetworkInfo?
    ) {
        if (ndkCrashLog == null) {
            return
        }
        val errorLogMessage = LOG_CRASH_MSG.format(Locale.US, ndkCrashLog.signalName)
        val logAttributes: Map<String, String>
        if (lastViewEvent != null) {
            logAttributes = mapOf(
                LogAttributes.RUM_SESSION_ID to lastViewEvent.session.id,
                LogAttributes.RUM_APPLICATION_ID to lastViewEvent.application.id,
                LogAttributes.RUM_VIEW_ID to lastViewEvent.view.id,
                LogAttributes.ERROR_STACK to ndkCrashLog.stacktrace
            )
            updateViewEventAndSendError(
                rumWriter,
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
            logWriter,
            errorLogMessage,
            logAttributes,
            ndkCrashLog,
            lastNetworkInfo,
            lastUserInfo
        )
    }

    private fun updateViewEventAndSendError(
        rumWriter: DataWriter<Any>,
        errorLogMessage: String,
        ndkCrashLog: NdkCrashLog,
        lastViewEvent: ViewEvent
    ) {
        val toSendErrorEvent = resolveErrorEventFromViewEvent(
            errorLogMessage,
            ndkCrashLog,
            lastViewEvent
        )
        rumWriter.write(toSendErrorEvent)
        val sessionsTimeDifference = System.currentTimeMillis() - lastViewEvent.date
        if (sessionsTimeDifference < VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD
        ) {
            val updatedViewEvent = updateViewEvent(lastViewEvent)
            rumWriter.write(updatedViewEvent)
        }
    }

    @SuppressWarnings("LongParameterList")
    private fun sendCrashLogEvent(
        logWriter: DataWriter<LogEvent>,
        errorLogMessage: String,
        logAttributes: Map<String, String>,
        ndkCrashLog: NdkCrashLog,
        lastNetworkInfo: NetworkInfo?,
        lastUserInfo: UserInfo?
    ) {
        val log = logGenerator.generateLog(
            level = LogGenerator.CRASH,
            errorLogMessage,
            null,
            logAttributes,
            emptySet(),
            ndkCrashLog.timestamp,
            bundleWithTraces = false,
            bundleWithRum = false,
            networkInfo = lastNetworkInfo,
            userInfo = lastUserInfo
        )

        logWriter.write(log)
    }

    private fun updateViewEvent(lastViewEvent: ViewEvent): ViewEvent {
        val currentCrash = lastViewEvent.view.crash
        val newCrash = currentCrash?.copy(count = currentCrash.count + 1) ?: ViewEvent.Crash(1)
        return lastViewEvent.copy(
            view = lastViewEvent.view.copy(
                crash = newCrash,
                isActive = false
            ),
            dd = lastViewEvent.dd.copy(
                documentVersion = lastViewEvent.dd.documentVersion + 1
            )
        )
    }

    private fun resolveErrorEventFromViewEvent(
        errorLogMessage: String,
        ndkCrashLog: NdkCrashLog,
        viewEvent: ViewEvent
    ): ErrorEvent {
        val connectivity = viewEvent.connectivity?.let {
            val connectivityStatus =
                ErrorEvent.Status.valueOf(it.status.name)
            val connectivityInterfaces = it.interfaces.map { ErrorEvent.Interface.valueOf(it.name) }
            val cellular = ErrorEvent.Cellular(
                it.cellular?.technology,
                it.cellular?.carrierName
            )
            ErrorEvent.Connectivity(connectivityStatus, connectivityInterfaces, cellular)
        }
        val additionalProperties = viewEvent.context?.additionalProperties ?: emptyMap()
        val additionalUserProperties = viewEvent.usr?.additionalProperties ?: emptyMap()
        return ErrorEvent(
            date = ndkCrashLog.timestamp + timeProvider.getServerOffsetMillis(),
            application = ErrorEvent.Application(viewEvent.application.id),
            service = viewEvent.service,
            session = ErrorEvent.ErrorEventSession(
                viewEvent.session.id,
                ErrorEvent.ErrorEventSessionType.USER
            ),
            source = rumEventSourceProvider.errorEventSource,
            view = ErrorEvent.View(
                id = viewEvent.view.id,
                name = viewEvent.view.name,
                referrer = viewEvent.view.referrer,
                url = viewEvent.view.url
            ),
            usr = ErrorEvent.Usr(
                viewEvent.usr?.id,
                viewEvent.usr?.name,
                viewEvent.usr?.email,
                additionalUserProperties
            ),
            connectivity = connectivity,
            dd = ErrorEvent.Dd(session = ErrorEvent.DdSession(plan = ErrorEvent.Plan.PLAN_1)),
            context = ErrorEvent.Context(additionalProperties = additionalProperties),
            error = ErrorEvent.Error(
                message = errorLogMessage,
                source = ErrorEvent.ErrorSource.SOURCE,
                stack = ndkCrashLog.stacktrace,
                isCrash = true,
                type = ndkCrashLog.signalName,
                sourceType = ErrorEvent.SourceType.ANDROID
            )
        )
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun clearCrashLog() {
        if (ndkCrashDataDirectory.existsSafe()) {
            try {
                ndkCrashDataDirectory.listFilesSafe()?.forEach { it.deleteRecursively() }
            } catch (e: Throwable) {
                internalLogger.errorWithTelemetry(
                    "Unable to clear the NDK crash report file:" +
                        " ${ndkCrashDataDirectory.absolutePath}",
                    e
                )
            }
        }
    }

    // endregion

    companion object {
        internal val VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD = TimeUnit.HOURS.toMillis(4)

        internal const val RUM_VIEW_EVENT_FILE_NAME = "last_view_event"
        internal const val CRASH_DATA_FILE_NAME = "crash_log"
        internal const val USER_INFO_FILE_NAME = "user_information"
        internal const val NETWORK_INFO_FILE_NAME = "network_information"

        internal const val LOGGER_NAME = "ndk_crash"

        internal const val LOG_CRASH_MSG = "NDK crash detected with signal: %s"
        internal const val ERROR_READ_NDK_DIR = "Error while trying to read the NDK crash directory"

        internal const val ERROR_TASK_REJECTED = "Unable to schedule operation on the executor"

        internal const val NDK_CRASH_REPORTS_FOLDER_NAME = "ndk_crash_reports"
        private const val NDK_CRASH_REPORTS_PENDING_FOLDER_NAME = "ndk_crash_reports_intermediary"

        internal const val DESERIALIZE_CRASH_EVENT_ERROR_MESSAGE =
            "Error while trying to deserialize the ndk crash log event"

        internal fun getNdkGrantedDir(context: Context): File {
            return File(context.cacheDir, NDK_CRASH_REPORTS_FOLDER_NAME)
        }

        internal fun getNdkPendingDir(context: Context): File {
            return File(context.cacheDir, NDK_CRASH_REPORTS_PENDING_FOLDER_NAME)
        }

        internal fun getLastViewEventFile(context: Context): File {
            return File(getNdkGrantedDir(context), RUM_VIEW_EVENT_FILE_NAME)
        }

        internal fun getPendingNetworkInfoFile(context: Context): File {
            return File(getNdkPendingDir(context), NETWORK_INFO_FILE_NAME)
        }

        internal fun getGrantedNetworkInfoFile(context: Context): File {
            return File(getNdkGrantedDir(context), NETWORK_INFO_FILE_NAME)
        }

        internal fun getPendingUserInfoFile(context: Context): File {
            return File(getNdkPendingDir(context), USER_INFO_FILE_NAME)
        }

        internal fun getGrantedUserInfoFile(context: Context): File {
            return File(getNdkGrantedDir(context), USER_INFO_FILE_NAME)
        }
    }
}
