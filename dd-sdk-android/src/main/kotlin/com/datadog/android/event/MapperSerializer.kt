/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.event

import com.datadog.android.core.internal.persistence.Serializer

internal class MapperSerializer<T : Any>(
    internal val eventMapper: EventMapper<T>,
    private val serializer: Serializer<T>
) : Serializer<T> {

    override fun serialize(model: T): String? {
        val mappedEvent = eventMapper.map(model) ?: return null
        return serializer.serialize(mappedEvent)
    }
}
