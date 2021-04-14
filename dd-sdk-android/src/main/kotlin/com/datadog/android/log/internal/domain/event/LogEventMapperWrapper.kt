/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain.event

import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.log.model.LogEvent
import java.util.Locale

internal class LogEventMapperWrapper(private val wrappedEventMapper: EventMapper<LogEvent>) :
    EventMapper<LogEvent> {
    override fun map(event: LogEvent): LogEvent? {
        val mappedEvent = wrappedEventMapper.map(event)
        if (mappedEvent == null) {
            devLogger.w(EVENT_NULL_WARNING_MESSAGE.format(Locale.US, event))
            return null
        }
        if (mappedEvent !== event) {
            devLogger.w(NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, event))
            return null
        }
        return mappedEvent
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
