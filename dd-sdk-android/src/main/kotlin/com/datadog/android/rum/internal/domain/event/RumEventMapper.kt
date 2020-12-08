/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.event.NoOpEventMapper
import com.datadog.android.event.EventMapper
import com.datadog.android.rum.domain.model.ActionEvent
import com.datadog.android.rum.domain.model.ErrorEvent
import com.datadog.android.rum.domain.model.ResourceEvent
import com.datadog.android.rum.domain.model.ViewEvent

internal data class RumEventMapper(
    val viewEventMapper: EventMapper<ViewEvent> = NoOpEventMapper(),
    val errorEventMapper: EventMapper<ErrorEvent> = NoOpEventMapper(),
    val resourceEventMapper: EventMapper<ResourceEvent> = NoOpEventMapper(),
    val actionEventMapper: EventMapper<ActionEvent>? = NoOpEventMapper()
) : EventMapper<RumEvent> {
    override fun map(event: RumEvent): RumEvent? {
        return event
    }
}
