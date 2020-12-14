/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.event.EventMapper
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent

internal data class RumEventMapper(
    val viewEventMapper: EventMapper<ViewEvent> = NoOpEventMapper(),
    val errorEventMapper: EventMapper<ErrorEvent> = NoOpEventMapper(),
    val resourceEventMapper: EventMapper<ResourceEvent> = NoOpEventMapper(),
    val actionEventMapper: EventMapper<ActionEvent> = NoOpEventMapper()
) : EventMapper<RumEvent> {

    override fun map(event: RumEvent): RumEvent? {
        val bundledMappedEvent = when (val bundledEvent = event.event) {
            is ViewEvent -> viewEventMapper.map(bundledEvent)
            is ActionEvent -> actionEventMapper.map(bundledEvent)
            is ErrorEvent -> errorEventMapper.map(bundledEvent)
            is ResourceEvent -> resourceEventMapper.map(bundledEvent)
            else -> {
                sdkLogger.w(
                    "RumEventMapper: there was no EventMapper assigned for" +
                        " RUM event type: [${bundledEvent.javaClass.simpleName}]"
                )
                bundledEvent
            }
        }

        // we need to check if the returned bundled mapped object is not null and same instance as the
        // original one. Otherwise we will drop the event.
        return if (bundledMappedEvent == null) {
            sdkLogger.i(
                "RumEventMapper: the returned mapped object was null." +
                    "This event will be dropped: [$event]"
            )
            null
        } else if (bundledMappedEvent !== event.event) {
            sdkLogger.w(
                "RumEventMapper: the returned mapped object was not the " +
                    "same instance as the original object. This event will be dropped: [$event]"
            )
            null
        } else {
            event
        }
    }
}
