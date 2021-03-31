/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain.event

import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.event.SpanEventMapper
import com.datadog.android.tracing.model.SpanEvent
import java.util.Locale

internal class SpanEventMapperWrapper(private val wrappedEventMapper: SpanEventMapper) :
    EventMapper<SpanEvent> {
    override fun map(event: SpanEvent): SpanEvent? {
        val mappedEvent = wrappedEventMapper.map(event)
        if (mappedEvent !== event) {
            devLogger.w(NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(Locale.US, event))
            return null
        }
        return mappedEvent
    }

    companion object {
        internal const val NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE =
            "SpanEventMapper: the returned mapped object was not the " +
                "same instance as the original object. This event will be dropped: %s"
    }
}
