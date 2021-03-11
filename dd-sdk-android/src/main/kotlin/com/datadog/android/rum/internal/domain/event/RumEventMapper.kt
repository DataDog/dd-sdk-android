/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.EventType
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import java.util.Locale

internal data class RumEventMapper(
    val viewEventMapper: EventMapper<ViewEvent> = NoOpEventMapper(),
    val errorEventMapper: EventMapper<ErrorEvent> = NoOpEventMapper(),
    val resourceEventMapper: EventMapper<ResourceEvent> = NoOpEventMapper(),
    val actionEventMapper: EventMapper<ActionEvent> = NoOpEventMapper()
) : EventMapper<RumEvent> {

    override fun map(event: RumEvent): RumEvent? {
        val mappedEvent = resolveEvent(event)
        if (mappedEvent == null) {
            notifyEventDropped(event)
        }
        return mappedEvent
    }

    // region Internal

    private fun mapRumEvent(event: RumEvent): Any? {
        return when (val bundledEvent = event.event) {
            is ViewEvent -> viewEventMapper.map(bundledEvent)
            is ActionEvent -> actionEventMapper.map(bundledEvent)
            is ErrorEvent -> errorEventMapper.map(bundledEvent)
            is ResourceEvent -> resourceEventMapper.map(bundledEvent)
            else -> {
                sdkLogger.w(
                    NO_EVENT_MAPPER_ASSIGNED_WARNING_MESSAGE
                        .format(Locale.US, bundledEvent.javaClass.simpleName)
                )
                bundledEvent
            }
        }
    }

    private fun resolveEvent(
        event: RumEvent
    ): RumEvent? {
        val bundledMappedEvent = mapRumEvent(event)

        // we need to check if the returned bundled mapped object is not null and same instance
        // as the original one. Otherwise we will drop the event.
        // In case the event is of type ViewEvent this cannot be null according with the interface
        // but it can happen that if used from Java code to have null values. In this case we will
        // log a warning and we will use the original event.
        return if (event.event is ViewEvent &&
            (bundledMappedEvent == null || bundledMappedEvent != event.event)
        ) {
            devLogger.w(
                VIEW_EVENT_NULL_WARNING_MESSAGE.format(Locale.US, event)
            )
            event
        } else if (bundledMappedEvent == null) {
            devLogger.w(
                EVENT_NULL_WARNING_MESSAGE.format(Locale.US, event)
            )
            null
        } else if (bundledMappedEvent !== event.event) {
            devLogger.w(
                NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, event)
            )
            null
        } else {
            event
        }
    }

    private fun notifyEventDropped(event: RumEvent) {
        val monitor = (GlobalRum.get() as? AdvancedRumMonitor) ?: return
        when (val rumEvent = event.event) {
            is ActionEvent -> monitor.eventDropped(rumEvent.view.id, EventType.ACTION)
            is ResourceEvent -> monitor.eventDropped(rumEvent.view.id, EventType.RESOURCE)
            is ErrorEvent -> monitor.eventDropped(rumEvent.view.id, EventType.ERROR)
            is LongTaskEvent -> monitor.eventDropped(rumEvent.view.id, EventType.LONG_TASK)
            else -> {
                // Nothing to do
            }
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
    }
}
