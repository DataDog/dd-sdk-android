/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.anr.AndroidTraceParser
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEventDeserializer
import com.datadog.android.rum.internal.domain.scope.toErrorSchemaType
import com.datadog.android.rum.internal.domain.scope.tryFromSource
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonObject
import java.util.concurrent.TimeUnit

internal class DatadogLateCrashReporter(
    private val sdkCore: InternalSdkCore,
    private val rumEventDeserializer: Deserializer<JsonObject, Any> = RumEventDeserializer(
        sdkCore.internalLogger
    ),
    private val androidTraceParser: AndroidTraceParser = AndroidTraceParser(sdkCore.internalLogger)
) : LateCrashReporter {

    // region LateCrashEventHandler

    @Suppress("ComplexCondition")
    override fun handleNdkCrashEvent(
        event: Map<*, *>,
        rumWriter: DataWriter<Any>
    ) {
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)

        if (rumFeature == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { INFO_RUM_FEATURE_NOT_REGISTERED }
            )
            return
        }

        val sourceType = event["sourceType"] as? String
        val timestamp = event["timestamp"] as? Long
        val timeSinceAppStartMs = event["timeSinceAppStartMs"] as? Long
        val signalName = event["signalName"] as? String
        val stacktrace = event["stacktrace"] as? String
        val errorLogMessage = event["message"] as? String
        val lastViewEvent = (event["lastViewEvent"] as? JsonObject)?.let {
            rumEventDeserializer.deserialize(it) as? ViewEvent
        }

        if (timestamp == null || signalName == null || stacktrace == null ||
            errorLogMessage == null || lastViewEvent == null
        ) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS }
            )
            return
        }

        rumFeature.withWriteContext { datadogContext, writeScope ->
            val toSendErrorEvent = resolveErrorEventFromViewEvent(
                datadogContext,
                ErrorEvent.SourceType.tryFromSource(sourceType),
                ErrorEvent.Category.EXCEPTION,
                errorLogMessage,
                timestamp,
                timeSinceAppStartMs,
                stacktrace,
                signalName,
                null,
                lastViewEvent
            )
            writeScope {
                rumWriter.write(it, toSendErrorEvent, EventType.CRASH)
                if (lastViewEvent.isWithinSessionAvailability) {
                    val updatedViewEvent = updateViewEvent(lastViewEvent)
                    rumWriter.write(it, updatedViewEvent, EventType.CRASH)
                }
            }
        }
    }

    @WorkerThread
    @RequiresApi(Build.VERSION_CODES.R)
    override fun handleAnrCrash(
        anrExitInfo: ApplicationExitInfo,
        lastRumViewEventJson: JsonObject,
        rumWriter: DataWriter<Any>
    ) {
        val lastViewEvent =
            rumEventDeserializer.deserialize(lastRumViewEventJson) as? ViewEvent ?: return

        val lastKnownViewStartedAt = lastViewEvent.date
        if (anrExitInfo.timestamp > lastKnownViewStartedAt) {
            val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)

            if (rumFeature == null) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { INFO_RUM_FEATURE_NOT_REGISTERED }
                )
                return
            }

            rumFeature.withWriteContext(
                withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)
            ) { datadogContext, writeScope ->
                // means we are too late, last view event belongs to the ongoing session
                if (lastViewEvent.session.id == datadogContext.rumSessionId) return@withWriteContext

                val lastFatalAnrSent = sdkCore.lastFatalAnrSent
                if (anrExitInfo.timestamp == lastFatalAnrSent) return@withWriteContext

                val threadDumps = readThreadsDump(anrExitInfo)
                if (threadDumps.isEmpty()) return@withWriteContext

                val toSendErrorEvent = resolveErrorEventFromViewEvent(
                    datadogContext,
                    ErrorEvent.SourceType.ANDROID,
                    ErrorEvent.Category.ANR,
                    ANRDetectorRunnable.ANR_MESSAGE,
                    anrExitInfo.timestamp,
                    // TODO RUM-3780 support reporting `error.time_since_app_start` for fatal ANRs
                    null,
                    threadDumps.mainThread?.stack.orEmpty(),
                    ANRException::class.java.canonicalName.orEmpty(),
                    threadDumps,
                    lastViewEvent
                )
                writeScope {
                    rumWriter.write(it, toSendErrorEvent, EventType.CRASH)
                    if (lastViewEvent.isWithinSessionAvailability) {
                        val updatedViewEvent = updateViewEvent(lastViewEvent)
                        rumWriter.write(it, updatedViewEvent, EventType.CRASH)
                    }
                    sdkCore.writeLastFatalAnrSent(anrExitInfo.timestamp)
                }
            }
        }
    }

    // endregion

    // region Internal

    @Suppress("LongMethod", "LongParameterList")
    private fun resolveErrorEventFromViewEvent(
        datadogContext: DatadogContext,
        sourceType: ErrorEvent.SourceType,
        category: ErrorEvent.Category,
        errorLogMessage: String,
        timestamp: Long,
        timeSinceAppStartMs: Long?,
        stacktrace: String,
        errorType: String,
        threadDumps: List<ThreadDump>?,
        viewEvent: ViewEvent
    ): ErrorEvent {
        val connectivity = viewEvent.connectivity?.let {
            val connectivityStatus =
                ErrorEvent.Status.valueOf(it.status.name)
            val connectivityInterfaces =
                it.interfaces?.map { ErrorEvent.Interface.valueOf(it.name) }
            val cellular = ErrorEvent.Cellular(
                it.cellular?.technology,
                it.cellular?.carrierName
            )
            ErrorEvent.Connectivity(connectivityStatus, connectivityInterfaces, cellular = cellular)
        }
        val additionalProperties = viewEvent.context?.additionalProperties ?: mutableMapOf()
        val additionalUserProperties = viewEvent.usr?.additionalProperties ?: mutableMapOf()
        val user = viewEvent.usr
        val hasUserInfo = user?.id != null || user?.name != null ||
            user?.email != null || additionalUserProperties.isNotEmpty()
        val deviceInfo = datadogContext.deviceInfo

        return ErrorEvent(
            // TODO RUM-3832 NDK/fatal ANRs reported should have build ID from the previous application run
            date = timestamp + datadogContext.time.serverTimeOffsetMs,
            buildId = datadogContext.appBuildId,
            application = ErrorEvent.Application(viewEvent.application.id),
            service = viewEvent.service,
            session = ErrorEvent.ErrorEventSession(
                viewEvent.session.id,
                ErrorEvent.ErrorEventSessionType.USER
            ),
            source = viewEvent.source?.toJson()?.asString?.let {
                ErrorEvent.ErrorEventSource.tryFromSource(
                    it,
                    sdkCore.internalLogger
                )
            },
            view = ErrorEvent.ErrorEventView(
                id = viewEvent.view.id,
                name = viewEvent.view.name,
                referrer = viewEvent.view.referrer,
                url = viewEvent.view.url
            ),
            usr = if (!hasUserInfo) {
                null
            } else {
                ErrorEvent.Usr(
                    user?.id,
                    user?.name,
                    user?.email,
                    user?.anonymousId,
                    additionalUserProperties
                )
            },
            connectivity = connectivity,
            os = ErrorEvent.Os(
                name = deviceInfo.osName,
                version = deviceInfo.osVersion,
                versionMajor = deviceInfo.osMajorVersion
            ),
            device = ErrorEvent.Device(
                type = deviceInfo.deviceType.toErrorSchemaType(),
                name = deviceInfo.deviceName,
                model = deviceInfo.deviceModel,
                brand = deviceInfo.deviceBrand,
                architecture = deviceInfo.architecture
            ),
            dd = ErrorEvent.Dd(
                session = ErrorEvent.DdSession(),
                configuration = ErrorEvent.Configuration(sessionSampleRate = viewEvent.sampleRate)
            ),
            context = ErrorEvent.Context(additionalProperties = additionalProperties),
            error = ErrorEvent.Error(
                message = errorLogMessage,
                source = ErrorEvent.ErrorSource.SOURCE,
                stack = stacktrace,
                isCrash = true,
                type = errorType,
                sourceType = sourceType,
                category = category,
                threads = threadDumps?.map {
                    ErrorEvent.Thread(
                        it.name,
                        it.crashed,
                        it.stack,
                        it.state
                    )
                },
                timeSinceAppStart = timeSinceAppStartMs
            ),
            version = viewEvent.version
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readThreadsDump(anrExitInfo: ApplicationExitInfo): List<ThreadDump> {
        val traceInputStream = anrExitInfo.traceInputStream
        if (traceInputStream == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { MISSING_ANR_TRACE }
            )
            return emptyList()
        }

        return androidTraceParser.parse(traceInputStream)
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

    private val ViewEvent.sampleRate: Float
        get() = dd.configuration?.sessionSampleRate?.toFloat() ?: 0f

    private val ViewEvent.isWithinSessionAvailability: Boolean
        get() {
            val now = System.currentTimeMillis()
            val sessionsTimeDifference = now - this.date
            return sessionsTimeDifference < VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD
        }

    private val List<ThreadDump>.mainThread: ThreadDump?
        get() = firstOrNull { it.name == "main" }

    private fun ErrorEvent.SourceType.Companion.tryFromSource(
        sourceType: String?
    ): ErrorEvent.SourceType {
        return if (sourceType != null) {
            try {
                ErrorEvent.SourceType.fromJson(sourceType)
            } catch (e: NoSuchElementException) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.TELEMETRY,
                    { "Error parsing source type from NDK crash event: $sourceType" },
                    e
                )
                ErrorEvent.SourceType.NDK
            }
        } else {
            ErrorEvent.SourceType.NDK
        }
    }

    private val DatadogContext.rumSessionId: String?
        get() = featuresContext[Feature.RUM_FEATURE_NAME]
            .orEmpty()[RumContext.SESSION_ID] as? String

    // endregion

    companion object {
        internal const val INFO_RUM_FEATURE_NOT_REGISTERED =
            "RUM feature is not registered, won't report NDK crash info as RUM error."
        internal const val NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS =
            "RUM feature received a NDK crash event" +
                " where one or more mandatory (timestamp, signalName, stacktrace," +
                " message, lastViewEvent) fields are either missing or have wrong type."
        internal const val MISSING_ANR_TRACE = "Last known exit reason has no trace information" +
            " attached, cannot report fatal ANR."

        internal val VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD = TimeUnit.HOURS.toMillis(4)
    }
}
