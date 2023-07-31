/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.event

import com.datadog.android.event.EventMapper
import com.datadog.android.trace.model.SpanEvent
import com.datadog.tools.annotation.NoOpImplementation

/**
 * An interface which can be implemented to modify the writable attributes inside a SpanEvent.
 */
@NoOpImplementation
interface SpanEventMapper : EventMapper<SpanEvent> {
    /**
     * By implementing this method you can intercept and modify the writable
     * attributes inside any event [SpanEvent] before it gets serialised.
     *
     * @param event the event to be serialised
     * @return the modified event [SpanEvent]. If the object returned has a different reference
     * than the object which was passed to the function, it will be dropped and will not be serialised.
     *
     */
    override fun map(event: SpanEvent): SpanEvent
}
