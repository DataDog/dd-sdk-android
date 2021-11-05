/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import android.content.Context
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
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
    private val timeProvider: TimeProvider
) : NdkCrashHandler {

    private val ndkCrashDataDirectory: File = getNdkGrantedDir(appContext)

    internal var lastSerializedRumViewEvent: String? = null
    internal var lastSerializedUserInformation: String? = null
    internal var lastSerializedNdkCrashLog: String? = null
    internal var lastSerializedNetworkInformation: String? = null

    // region NdkCrashHandler

    override fun prepareData() {
        dataPersistenceExecutorService.submit {
            readCrashData()
        }
    }

    override fun handleNdkCrash(
        logWriter: DataWriter<LogEvent>,
        rumWriter: DataWriter<Any>
    ) {
        dataPersistenceExecutorService.submit {
            checkAndHandleNdkCrashReport(logWriter, rumWriter)
        }
    }

    // endregion

    // region Internal

    private fun readCrashData() {
        if (!ndkCrashDataDirectory.exists()) {
            return
        }
        try {
            ndkCrashDataDirectory.listFiles()?.forEach {
                when (it.name) {
                    CRASH_DATA_FILE_NAME -> lastSerializedNdkCrashLog = it.readText()
                    RUM_VIEW_EVENT_FILE_NAME -> lastSerializedRumViewEvent = it.readText()
                    USER_INFO_FILE_NAME -> lastSerializedUserInformation = it.readText()
                    NETWORK_INFO_FILE_NAME -> lastSerializedNetworkInformation = it.readText()
                }
            }
        } catch (e: SecurityException) {
            internalLogger.e(ERROR_READ_NDK_DIR, e)
        } finally {
            clearCrashLog()
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
                source = ErrorEvent.Source.SOURCE,
                stack = ndkCrashLog.stacktrace,
                isCrash = true,
                type = ndkCrashLog.signalName,
                sourceType = ErrorEvent.SourceType.ANDROID
            )
        )
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun clearCrashLog() {
        if (ndkCrashDataDirectory.exists()) {
            try {
                ndkCrashDataDirectory.listFiles()?.forEach { it.deleteRecursively() }
            } catch (e: Throwable) {
                internalLogger.e(
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

        internal fun getCrashDataFile(context: Context): File {
            return File(getNdkGrantedDir(context), CRASH_DATA_FILE_NAME)
        }
    }
}
