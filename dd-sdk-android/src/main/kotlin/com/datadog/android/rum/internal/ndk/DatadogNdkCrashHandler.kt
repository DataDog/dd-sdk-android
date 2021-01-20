/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.domain.Deserializer
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.rum.internal.data.file.RumFileWriter
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventDeserializer
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

internal class DatadogNdkCrashHandler(
    private val ndkCrashDataDirectory: File,
    private val dataPersistenceExecutorService: ExecutorService,
    private val asyncLogWriter: Writer<Log>,
    private val asyncRumWriter: Writer<RumEvent>,
    private val logGenerator: LogGenerator,
    private val rumEventDeserializer: Deserializer<RumEvent> = RumEventDeserializer()
) : NdkCrashHandler {

    override fun handleNdkCrash() {
        dataPersistenceExecutorService.submit {
            checkAndHandleNdkCrashReport()
        }
    }

    private fun checkAndHandleNdkCrashReport() {
        if (ndkCrashDataDirectory.exists()) {
            val ndkCrashLog = findLastNdCrashLog()
            val lastRumViewEvent = findLastRumViewEvent()
            handleNdkCrashLog(ndkCrashLog, lastRumViewEvent)
            clearCrashLog()
        }
    }

    private fun findLastNdCrashLog(): NdkCrashLog? {
        val crashLogFile =
            ndkCrashDataDirectory
                .listFiles { _, name -> name == CRASH_LOG_FILE_NAME }
                ?.firstOrNull()
        if (crashLogFile != null) {
            return readFromFile(
                crashLogFile,
                NdkCrashLog::class.java,
                "Malformed ndk crash error log",
                "Error while trying to read the ndk crash log"
            )
        }
        return null
    }

    private fun findLastRumViewEvent(): RumEvent? {
        val lastViewEventFile =
            ndkCrashDataDirectory.listFiles { _, name ->
                name == RumFileWriter.LAST_VIEW_EVENT_FILE_NAME
            }?.firstOrNull()
        if (lastViewEventFile != null) {
            return readFromFile(
                lastViewEventFile,
                RumEvent::class.java,
                "Malformed RUM ViewEvent log",
                "Error while trying to read the last rum view event log"
            )
        }
        return null
    }

    private fun <T> readFromFile(
        file: File,
        type: Class<T>,
        parsingErrorMessage: String,
        ioErrorMessage: String
    ): T? {
        return try {
            val serializedData = file.readText(Charsets.UTF_8)
            @Suppress("UNCHECKED_CAST")
            if (type == RumEvent::class.java) {
                rumEventDeserializer.deserialize(serializedData) as? T
            } else {
                NdkCrashLog.fromJson(serializedData) as T
            }
        } catch (e: JsonSyntaxException) {
            sdkLogger.e(parsingErrorMessage, e)
            null
        } catch (e: IOException) {
            sdkLogger.e(ioErrorMessage, e)
            null
        }
    }

    private fun handleNdkCrashLog(ndkCrashLog: NdkCrashLog?, lastRumViewEvent: RumEvent?) {
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

        sendCrashLogEvent(errorLogMessage, logAttributes, ndkCrashLog)
    }

    private fun updateViewEventAndSendError(
        errorLogMessage: String,
        ndkCrashLog: NdkCrashLog,
        lastRumViewEvent: RumEvent,
        bundledViewEvent: ViewEvent
    ) {
        // update the error count
        val toSendErrorEvent = resolveFromLastRumViewEvent(
            errorLogMessage,
            ndkCrashLog,
            lastRumViewEvent,
            bundledViewEvent
        )
        val sessionsTimeDifference = System.currentTimeMillis() - ndkCrashLog.timestamp
        if (sessionsTimeDifference < VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD
        ) {
            val toSendRumEvent = resolveFromLastRumViewEvent(lastRumViewEvent, bundledViewEvent)
            asyncRumWriter.write(toSendRumEvent)
        }
        asyncRumWriter.write(toSendErrorEvent)
    }

    private fun sendCrashLogEvent(
        errorLogMessage: String,
        logAttributes: Map<String, String>,
        ndkCrashLog: NdkCrashLog
    ) {
        val log = logGenerator.generateLog(
            Log.CRASH,
            errorLogMessage,
            null,
            logAttributes,
            emptySet(),
            ndkCrashLog.timestamp,
            bundleWithTraces = false,
            bundleWithRum = false
        )

        asyncLogWriter.write(log)
    }

    private fun resolveFromLastRumViewEvent(
        lastRumViewEvent: RumEvent,
        bundledViewEvent: ViewEvent
    ): RumEvent {
        return lastRumViewEvent.copy(
            event = bundledViewEvent.copy(
                view = bundledViewEvent.view.copy(
                    error = bundledViewEvent.view.error
                        .copy(count = bundledViewEvent.view.error.count + 1),
                    isActive = false
                ),
                dd = bundledViewEvent.dd.copy(
                    documentVersion = bundledViewEvent.dd.documentVersion + 1
                )
            )
        )
    }

    private fun resolveFromLastRumViewEvent(
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
            rumViewEvent.userExtraAttributes,
            rumViewEvent.customTimings
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
        const val CRASH_LOG_FILE_NAME = "crash_log"
        const val LOGGER_NAME = "ndk_crash"
        const val NDK_ERROR_LOG_MESSAGE = "NDK crash detected with signal: %s"
        internal const val NDK_CRASH_REPORTS_FOLDER_NAME = "ndk_crash_reports"
    }
}
