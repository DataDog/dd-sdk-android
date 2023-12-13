/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.rum.internal.domain.event.RumEventDeserializer
import com.datadog.android.rum.internal.domain.scope.toErrorSchemaType
import com.datadog.android.rum.internal.domain.scope.tryFromSource
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonObject
import java.util.concurrent.TimeUnit

internal class DatadogNdkCrashEventHandler(
    private val internalLogger: InternalLogger,
    private val rumEventDeserializer: Deserializer<JsonObject, Any> = RumEventDeserializer(internalLogger)
) : NdkCrashEventHandler {

    @Suppress("ComplexCondition")
    override fun handleEvent(event: Map<*, *>, sdkCore: FeatureSdkCore, rumWriter: DataWriter<Any>) {
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)

        if (rumFeature == null) {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { INFO_RUM_FEATURE_NOT_REGISTERED }
            )
            return
        }

        val timestamp = event["timestamp"] as? Long
        val signalName = event["signalName"] as? String
        val stacktrace = event["stacktrace"] as? String
        val errorLogMessage = event["message"] as? String
        val lastViewEvent = (event["lastViewEvent"] as? JsonObject)?.let {
            rumEventDeserializer.deserialize(it) as? ViewEvent
        }

        val sampleRate = lastViewEvent?.dd?.configuration?.sessionSampleRate?.toFloat() ?: 0f

        if (timestamp == null || signalName == null || stacktrace == null ||
            errorLogMessage == null || lastViewEvent == null
        ) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS }
            )
            return
        }

        val now = System.currentTimeMillis()
        rumFeature.withWriteContext { datadogContext, eventBatchWriter ->
            val toSendErrorEvent = resolveErrorEventFromViewEvent(
                datadogContext,
                errorLogMessage,
                timestamp,
                stacktrace,
                signalName,
                lastViewEvent,
                sampleRate
            )
            @Suppress("ThreadSafety") // called in a worker thread context
            rumWriter.write(eventBatchWriter, toSendErrorEvent)
            val sessionsTimeDifference = now - lastViewEvent.date
            if (sessionsTimeDifference < VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD) {
                val updatedViewEvent = updateViewEvent(lastViewEvent)
                @Suppress("ThreadSafety") // called in a worker thread context
                rumWriter.write(eventBatchWriter, updatedViewEvent)
            }
        }
    }

    @Suppress("LongMethod", "LongParameterList")
    private fun resolveErrorEventFromViewEvent(
        datadogContext: DatadogContext,
        errorLogMessage: String,
        timestamp: Long,
        stacktrace: String,
        signalName: String,
        viewEvent: ViewEvent,
        sampleRate: Float
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
        val additionalProperties = viewEvent.context?.additionalProperties ?: mutableMapOf()
        val additionalUserProperties = viewEvent.usr?.additionalProperties ?: mutableMapOf()
        val user = viewEvent.usr
        val hasUserInfo = user?.id != null || user?.name != null ||
            user?.email != null || additionalUserProperties.isNotEmpty()
        val deviceInfo = datadogContext.deviceInfo

        return ErrorEvent(
            date = timestamp + datadogContext.time.serverTimeOffsetMs,
            application = ErrorEvent.Application(viewEvent.application.id),
            service = viewEvent.service,
            session = ErrorEvent.ErrorEventSession(
                viewEvent.session.id,
                ErrorEvent.ErrorEventSessionType.USER
            ),
            source = viewEvent.source?.toJson()?.asString?.let {
                ErrorEvent.ErrorEventSource.tryFromSource(
                    it,
                    internalLogger
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
                session = ErrorEvent.DdSession(plan = ErrorEvent.Plan.PLAN_1),
                configuration = ErrorEvent.Configuration(sessionSampleRate = sampleRate)
            ),
            context = ErrorEvent.Context(additionalProperties = additionalProperties),
            error = ErrorEvent.Error(
                message = errorLogMessage,
                source = ErrorEvent.ErrorSource.SOURCE,
                stack = stacktrace,
                isCrash = true,
                type = signalName,
                sourceType = ErrorEvent.SourceType.ANDROID
            ),
            version = viewEvent.version
        )
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

    // endregion

    companion object {
        internal const val INFO_RUM_FEATURE_NOT_REGISTERED =
            "RUM feature is not registered, won't report NDK crash info as RUM error."
        internal const val NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS =
            "RUM feature received a NDK crash event" +
                " where one or more mandatory (timestamp, signalName, stacktrace," +
                " message, lastViewEvent) fields are either missing or have wrong type."

        internal val VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD = TimeUnit.HOURS.toMillis(4)
    }
}
