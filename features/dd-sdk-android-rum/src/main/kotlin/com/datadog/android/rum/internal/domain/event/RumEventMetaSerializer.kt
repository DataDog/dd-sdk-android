/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.persistence.Serializer

internal class RumEventMetaSerializer : Serializer<RumEventMeta> {
    override fun serialize(model: RumEventMeta): String {
        return when (model) {
            is RumEventMeta.View -> model.toJson().toString()
        }
    }
}
