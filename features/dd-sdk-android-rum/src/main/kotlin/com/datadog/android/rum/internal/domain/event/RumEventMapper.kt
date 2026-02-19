/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.VitalAppLaunchEvent
import com.datadog.android.rum.model.VitalOperationStepEvent
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent

internal data class RumEventMapper(
    val viewEventMapper: EventMapper<ViewEvent> = NoOpEventMapper(),
    val errorEventMapper: EventMapper<ErrorEvent> = NoOpEventMapper(),
    val resourceEventMapper: EventMapper<ResourceEvent> = NoOpEventMapper(),
    val actionEventMapper: EventMapper<ActionEvent> = NoOpEventMapper(),
    val longTaskEventMapper: EventMapper<LongTaskEvent> = NoOpEventMapper(),
    val vitalOperationStepEventMapper: EventMapper<VitalOperationStepEvent> = NoOpEventMapper(),
    val vitalAppLaunchEventMapper: EventMapper<VitalAppLaunchEvent> = NoOpEventMapper(),
    val telemetryConfigurationMapper: EventMapper<TelemetryConfigurationEvent> = NoOpEventMapper(),
    private val internalLogger: InternalLogger
) : EventMapper<Any> {

    private val logger = DdSdkAndroidRumLogger(internalLogger)

    override fun map(event: Any): Any? {
        return resolveEvent(event)
    }

    // region Internal

    private fun mapRumEvent(event: Any): Any? {
        return when (event) {
            is ViewEvent -> viewEventMapper.map(event)
            is ActionEvent -> actionEventMapper.map(event)
            is ErrorEvent -> {
                // Don't allow the error event to be dropped if it's a crash
                if (event.error.isCrash == true) {
                    val mappedEvent = errorEventMapper.map(event)
                    if (mappedEvent == null) {
                        logger.logNoDroppingFatalErrors()
                        event
                    } else {
                        mappedEvent
                    }
                } else {
                    errorEventMapper.map(event)
                }
            }
            is ResourceEvent -> resourceEventMapper.map(event)
            is LongTaskEvent -> longTaskEventMapper.map(event)
            is VitalOperationStepEvent -> vitalOperationStepEventMapper.map(event)
            is VitalAppLaunchEvent -> vitalAppLaunchEventMapper.map(event)
            is TelemetryConfigurationEvent -> telemetryConfigurationMapper.map(event)
            is TelemetryDebugEvent,
            is TelemetryUsageEvent,
            is TelemetryErrorEvent -> event
            else -> {
                logger.logNoEventMapperAssigned(eventType = event.javaClass.simpleName)
                event
            }
        }
    }

    private fun resolveEvent(
        event: Any
    ): Any? {
        val mappedEvent = mapRumEvent(event)

        // we need to check if the returned bundled mapped object is not null and same instance
        // as the original one. Otherwise we will drop the event.
        // In case the event is of type ViewEvent this cannot be null according with the interface
        // but it can happen that if used from Java code to have null values. In this case we will
        // log a warning and we will use the original event.
        return if (event is ViewEvent && (mappedEvent == null || mappedEvent !== event)) {
            logger.logViewEventNull(event = event.toString())
            event
        } else if (mappedEvent == null) {
            logger.logEventNull(event = event.toString())
            null
        } else if (mappedEvent !== event) {
            logger.logNotSameEventInstance(event = event.toString())
            null
        } else {
            event
        }
    }

    // endregion

}
