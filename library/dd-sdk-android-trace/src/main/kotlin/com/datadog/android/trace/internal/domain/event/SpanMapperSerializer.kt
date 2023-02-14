/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

import com.datadog.android.core.persistence.Serializer
import com.datadog.android.event.EventMapper
import com.datadog.android.trace.model.SpanEvent
import com.datadog.opentracing.DDSpan

internal class SpanMapperSerializer(
    private val legacyMapper: Mapper<DDSpan, SpanEvent>,
    internal val spanEventMapper: EventMapper<SpanEvent>,
    private val spanSerializer: Serializer<SpanEvent>
) : Serializer<DDSpan> {

    override fun serialize(model: DDSpan): String? {
        val spanEvent = legacyMapper.map(model)
        val mappedEvent = spanEventMapper.map(spanEvent) ?: return null
        return spanSerializer.serialize(mappedEvent)
    }
}
