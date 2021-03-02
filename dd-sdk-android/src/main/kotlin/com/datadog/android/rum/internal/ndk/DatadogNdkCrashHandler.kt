/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.domain.Deserializer
import com.datadog.android.core.internal.net.info.NetworkInfoDeserializer
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.user.UserInfoDeserializer
import com.datadog.android.rum.internal.data.file.RumFileWriter
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventDeserializer
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonParseException
import java.io.File
import java.lang.IllegalStateException
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

internal class DatadogNdkCrashHandler(
    private val ndkCrashDataDirectory: File,
    private val dataPersistenceExecutorService: ExecutorService,
    private val logGenerator: LogGenerator,
    private val rumEventDeserializer: Deserializer<RumEvent> = RumEventDeserializer(),
    private val networkInfoDeserializer: Deserializer<NetworkInfo> = NetworkInfoDeserializer(),
    private val userInfoDeserializer: Deserializer<UserInfo> = UserInfoDeserializer()

) : NdkCrashHandler {

    internal var lastSerializedRumViewEvent: String? = null
    internal var lastSerializedUserInformation: String? = null
    internal var lastSerializedNdkCrashLog: String? = null
    internal var lastSerializedNetworkInformation: String? = null

    override fun prepareData() {
        dataPersistenceExecutorService.submit {
            readCrashData()
        }
    }

    override fun handleNdkCrash(
        logWriter: Writer<Log>,
        rumWriter: Writer<RumEvent>
    ) {
        dataPersistenceExecutorService.submit {
            checkAndHandleNdkCrashReport(logWriter, rumWriter)
        }
    }

    private fun readCrashData() {
        try {
            if (!ndkCrashDataDirectory.exists()) {
                return
            }
            ndkCrashDataDirectory.listFiles()?.forEach {
                when (it.name) {
                    LAST_CRASH_LOG_FILE_NAME -> lastSerializedNdkCrashLog = it.readText()
                    RumFileWriter.LAST_VIEW_EVENT_FILE_NAME ->
                        lastSerializedRumViewEvent =
                            it.readText()
                    LAST_USER_INFORMATION_FILE_NAME -> lastSerializedUserInformation = it.readText()
                    LAST_NETWORK_INFORMATION_FILE_NAME ->
                        lastSerializedNetworkInformation =
                            it.readText()
                }
            }
        } catch (e: SecurityException) {
            devLogger.e(READ_NDK_DIRECTORY_ERROR_MESSAGE, e)
        } finally {
            clearCrashLog()
        }
    }

    private fun checkAndHandleNdkCrashReport(
        logWriter: Writer<Log>,
        rumWriter: Writer<RumEvent>
    ) {
        val lastSerializedRumViewEvent = lastSerializedRumViewEvent
        val lastSerializedUserInformation: String? = lastSerializedUserInformation
        val lastSerializedNdkCrashLog: String? = lastSerializedNdkCrashLog
        val lastSerializedNetworkInformation: String? = lastSerializedNetworkInformation
        if (lastSerializedNdkCrashLog != null) {
            try {
                val lastNdkCrashLog = NdkCrashLog.fromJson(lastSerializedNdkCrashLog)
                val lastRumViewEvent =
                    lastSerializedRumViewEvent?.let { rumEventDeserializer.deserialize(it) }
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
            } catch (e: JsonParseException) {
                sdkLogger.e(DESERIALIZE_CRASH_EVENT_ERROR_MESSAGE, e)
            } catch (e: IllegalStateException) {
                sdkLogger.e(DESERIALIZE_CRASH_EVENT_ERROR_MESSAGE, e)
            }
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
        logWriter: Writer<Log>,
        rumWriter: Writer<RumEvent>,
        ndkCrashLog: NdkCrashLog?,
        lastRumViewEvent: RumEvent?,
        lastUserInfo: UserInfo?,
        lastNetworkInfo: NetworkInfo?
    ) {
        if (ndkCrashLog == null) {
            return
        }
        val errorLogMessage = NDK_ERROR_LOG_MESSAGE.format(ndkCrashLog.signalName)
        val bundledViewEvent = lastRumViewEvent?.event as? ViewEvent
        val logAttributes: Map<String, String>
        if (lastRumViewEvent != null && bundledViewEvent != null) {
            logAttributes = mapOf(
                LogAttributes.RUM_SESSION_ID to bundledViewEvent.session.id,
                LogAttributes.RUM_APPLICATION_ID to bundledViewEvent.application.id,
                LogAttributes.RUM_VIEW_ID to bundledViewEvent.view.id,
                LogAttributes.ERROR_STACK to ndkCrashLog.stacktrace
            )
            updateViewEventAndSendError(
                rumWriter,
                errorLogMessage,
                ndkCrashLog,
                lastRumViewEvent,
                bundledViewEvent
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
        rumWriter: Writer<RumEvent>,
        errorLogMessage: String,
        ndkCrashLog: NdkCrashLog,
        lastRumViewEvent: RumEvent,
        bundledViewEvent: ViewEvent
    ) {
        // update the crash count
        val toSendErrorEvent = resolveErrorEventFromViewEvent(
            errorLogMessage,
            ndkCrashLog,
            lastRumViewEvent,
            bundledViewEvent
        )
        val sessionsTimeDifference = System.currentTimeMillis() - bundledViewEvent.date
        if (sessionsTimeDifference < VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD
        ) {
            val toSendRumEvent =
                resolveViewEventFromLastOne(lastRumViewEvent, bundledViewEvent)
            rumWriter.write(toSendRumEvent)
            sdkLogger.i(
                "The crash.count attribute was" +
                    " incremented on the view with url: ${bundledViewEvent.view.url}"
            )
        }
        rumWriter.write(toSendErrorEvent)
        sdkLogger.i(
            "A new RUM error event was sent" +
                " related with NDK crash: ${ndkCrashLog.signalName}"
        )
    }

    @SuppressWarnings("LongParameterList")
    private fun sendCrashLogEvent(
        logWriter: Writer<Log>,
        errorLogMessage: String,
        logAttributes: Map<String, String>,
        ndkCrashLog: NdkCrashLog,
        lastNetworkInfo: NetworkInfo?,
        lastUserInfo: UserInfo?
    ) {
        val log = logGenerator.generateLog(
            Log.CRASH,
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
        sdkLogger.i(
            "NDK crash log with " +
                "signal name: ${ndkCrashLog.signalName} was sent"
        )
    }

    private fun resolveViewEventFromLastOne(
        lastRumViewEvent: RumEvent,
        bundledViewEvent: ViewEvent
    ): RumEvent {
        val currentCrash = bundledViewEvent.view.crash
        val newCrash = currentCrash?.copy(count = currentCrash.count + 1) ?: ViewEvent.Crash(1)
        return lastRumViewEvent.copy(
            event = bundledViewEvent.copy(
                view = bundledViewEvent.view.copy(
                    crash = newCrash,
                    isActive = false
                ),
                dd = bundledViewEvent.dd.copy(
                    documentVersion = bundledViewEvent.dd.documentVersion + 1
                )
            )
        )
    }

    private fun resolveErrorEventFromViewEvent(
        errorLogMessage: String,
        ndkCrashLog: NdkCrashLog,
        rumViewEvent: RumEvent,
        bundledViewEvent: ViewEvent
    ): RumEvent {
        val connectivity = bundledViewEvent.connectivity?.let {
            val connectivityStatus =
                ErrorEvent.Status.valueOf(it.status.name)
            val connectivityInterfaces = it.interfaces.map { ErrorEvent.Interface.valueOf(it.name) }
            val cellular = ErrorEvent.Cellular(
                it.cellular?.technology,
                it.cellular?.carrierName
            )
            ErrorEvent.Connectivity(connectivityStatus, connectivityInterfaces, cellular)
        }
        return RumEvent(
            ErrorEvent(
                ndkCrashLog.timestamp,
                ErrorEvent.Application(bundledViewEvent.application.id),
                bundledViewEvent.service,
                ErrorEvent.Session(bundledViewEvent.session.id, ErrorEvent.SessionType.USER),
                ErrorEvent.View(
                    bundledViewEvent.view.id,
                    bundledViewEvent.view.referrer,
                    bundledViewEvent.view.url
                ),
                ErrorEvent.Usr(
                    bundledViewEvent.usr?.id,
                    bundledViewEvent.usr?.name,
                    bundledViewEvent.usr?.email
                ),
                connectivity,
                ErrorEvent.Dd(),
                ErrorEvent.Error(
                    errorLogMessage,
                    ErrorEvent.Source.SOURCE,
                    ndkCrashLog.stacktrace,
                    true,
                    ndkCrashLog.signalName
                )
            ),
            rumViewEvent.globalAttributes,
            rumViewEvent.userExtraAttributes
        )
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun clearCrashLog() {
        if (ndkCrashDataDirectory.exists()) {
            try {
                ndkCrashDataDirectory.listFiles()?.forEach { it.deleteRecursively() }
            } catch (e: Throwable) {
                sdkLogger.e(
                    "Unable to clear the NDK crash report file:" +
                        " ${ndkCrashDataDirectory.absolutePath}",
                    e
                )
            }
        }
    }

    companion object {
        internal val VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD = TimeUnit.HOURS.toMillis(4)
        internal const val LAST_CRASH_LOG_FILE_NAME = "crash_log"
        internal const val LAST_USER_INFORMATION_FILE_NAME = "user_information"
        internal const val LAST_NETWORK_INFORMATION_FILE_NAME = "network_information"
        internal const val LOGGER_NAME = "ndk_crash"
        internal const val NDK_ERROR_LOG_MESSAGE = "NDK crash detected with signal: %s"
        internal const val READ_NDK_DIRECTORY_ERROR_MESSAGE =
            "Error while trying to read the NDK crash directory"
        internal const val NDK_CRASH_REPORTS_FOLDER_NAME = "ndk_crash_reports"
        internal const val NDK_CRASH_REPORTS_INTERMEDIARY_FOLDER_NAME =
            "ndk_crash_reports_intermediary"
        internal const val DESERIALIZE_CRASH_EVENT_ERROR_MESSAGE =
            "Error while trying to deserialize the ndk crash log event"
    }
}
