/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.core.internal.persistence.Serializer

internal class RecordSerializer : Serializer<Any> {
    override fun serialize(model: Any): String? {
        // TODO: This will be switched to a Serializer<Record> once the models
        //  will be in place. RUMM-2330"
        return null
    }
}
