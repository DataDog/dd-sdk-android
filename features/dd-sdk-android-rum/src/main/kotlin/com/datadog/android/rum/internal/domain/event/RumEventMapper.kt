/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import java.util.Locale

internal data class RumEventMapper(
    val viewEventMapper: EventMapper<ViewEvent> = NoOpEventMapper(),
    val errorEventMapper: EventMapper<ErrorEvent> = NoOpEventMapper(),
    val resourceEventMapper: EventMapper<ResourceEvent> = NoOpEventMapper(),
    val actionEventMapper: EventMapper<ActionEvent> = NoOpEventMapper(),
    val longTaskEventMapper: EventMapper<LongTaskEvent> = NoOpEventMapper(),
    val telemetryConfigurationMapper: EventMapper<TelemetryConfigurationEvent> = NoOpEventMapper(),
    private val internalLogger: InternalLogger
) : EventMapper<Any> {

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
                        internalLogger.log(
                            InternalLogger.Level.WARN,
                            InternalLogger.Target.USER,
                            { NO_DROPPING_FATAL_ERRORS_WARNING_MESSAGE }
                        )
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
            is TelemetryConfigurationEvent -> telemetryConfigurationMapper.map(event)
            is TelemetryDebugEvent,
            is TelemetryErrorEvent -> event
            else -> {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    {
                        NO_EVENT_MAPPER_ASSIGNED_WARNING_MESSAGE
                            .format(Locale.US, event.javaClass.simpleName)
                    }
                )
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
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { VIEW_EVENT_NULL_WARNING_MESSAGE.format(Locale.US, event) }
            )
            event
        } else if (mappedEvent == null) {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { EVENT_NULL_WARNING_MESSAGE.format(Locale.US, event) }
            )
            null
        } else if (mappedEvent !== event) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, event) }
            )
            null
        } else {
            event
        }
    }

    // endregion

    companion object {
        internal const val VIEW_EVENT_NULL_WARNING_MESSAGE =
            "RumEventMapper: the returned mapped ViewEvent was null. " +
                "The original event object will be used instead: %s"
        internal const val EVENT_NULL_WARNING_MESSAGE =
            "RumEventMapper: the returned mapped object was null. " +
                "This event will be dropped: %s"
        internal const val NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE =
            "RumEventMapper: the returned mapped object was not the " +
                "same instance as the original object. This event will be dropped: %s"
        internal const val NO_EVENT_MAPPER_ASSIGNED_WARNING_MESSAGE =
            "RumEventMapper: there was no EventMapper assigned for" +
                " RUM event type: %s"
        internal const val NO_DROPPING_FATAL_ERRORS_WARNING_MESSAGE =
            "RumEventMapper: the return from the ErrorEvent mapper was null for a crash. " +
                "Dropping crashes in from the event mapper is not supported. " +
                "The original event object will be used instead."
    }
}
