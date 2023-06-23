/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain.event

import com.datadog.android.event.EventMapper
import com.datadog.android.log.model.LogEvent
import com.datadog.android.v2.api.InternalLogger
import java.util.Locale

internal class LogEventMapperWrapper(
    internal val wrappedEventMapper: EventMapper<LogEvent>,
    internal val internalLogger: InternalLogger
) : EventMapper<LogEvent> {

    override fun map(event: LogEvent): LogEvent? {
        val mappedEvent = wrappedEventMapper.map(event)
        return if (mappedEvent == null) {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { EVENT_NULL_WARNING_MESSAGE.format(Locale.US, event) }
            )
            null
        } else if (mappedEvent !== event) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, event) }
            )
            null
        } else {
            mappedEvent
        }
    }

    companion object {

        internal const val EVENT_NULL_WARNING_MESSAGE =
            "LogEventMapper: the returned mapped object was null. " +
                "This event will be dropped: %s"

        internal const val NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE =
            "LogEventMapper: the returned mapped object was not the " +
                "same instance as the original object. This event will be dropped: %s"
    }
}
