/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.persistence.Serializer
import com.google.gson.JsonObject

internal class RumEventMetaSerializer : Serializer<Any> {
    override fun serialize(model: Any): String {
        return when (model) {
            is RumEventMeta.View -> model.toJson().toString()
            else -> JsonObject().toString()
        }
    }
}
