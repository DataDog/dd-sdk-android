/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.readTextSafe
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.join
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.domain.DatadogLogGenerator
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.utils.errorWithTelemetry
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.internal.domain.scope.toErrorSchemaType
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
internal class DatadogNdkCrashHandler(
    storageDir: File,
    private val dataPersistenceExecutorService: ExecutorService,
    internal val logGenerator: LogGenerator,
    private val ndkCrashLogDeserializer: Deserializer<NdkCrashLog>,
    private val rumEventDeserializer: Deserializer<Any>,
    private val networkInfoDeserializer: Deserializer<NetworkInfo>,
    private val userInfoDeserializer: Deserializer<UserInfo>,
    private val internalLogger: Logger,
    internal val timeProvider: TimeProvider,
    private val rumFileReader: BatchFileReader,
    private val envFileReader: FileReader,
    private val rumEventSourceProvider: RumEventSourceProvider,
    private val androidInfoProvider: AndroidInfoProvider
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
                @Suppress("ThreadSafety")
                checkAndHandleNdkCrashReport(logWriter, rumWriter)
            }
        } catch (e: RejectedExecutionException) {
            internalLogger.errorWithTelemetry(ERROR_TASK_REJECTED, e)
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
            internalLogger.errorWithTelemetry(ERROR_READ_NDK_DIR, e)
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
    @WorkerThread
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

    @WorkerThread
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
    @WorkerThread
    private fun sendCrashLogEvent(
        logWriter: DataWriter<LogEvent>,
        errorLogMessage: String,
        logAttributes: Map<String, String>,
        ndkCrashLog: NdkCrashLog,
        lastNetworkInfo: NetworkInfo?,
        lastUserInfo: UserInfo?
    ) {
        val log = logGenerator.generateLog(
            level = DatadogLogGenerator.CRASH,
            errorLogMessage,
            null,
            logAttributes,
            emptySet(),
            ndkCrashLog.timestamp,
            bundleWithTraces = false,
            bundleWithRum = false,
            networkInfo = lastNetworkInfo,
            userInfo = lastUserInfo
        ) ?: return

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
            os = ErrorEvent.Os(
                name = androidInfoProvider.osName,
                version = androidInfoProvider.osVersion,
                versionMajor = androidInfoProvider.osMajorVersion
            ),
            device = ErrorEvent.Device(
                type = androidInfoProvider.deviceType.toErrorSchemaType(),
                name = androidInfoProvider.deviceName,
                model = androidInfoProvider.deviceModel,
                brand = androidInfoProvider.deviceBrand
            ),
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
