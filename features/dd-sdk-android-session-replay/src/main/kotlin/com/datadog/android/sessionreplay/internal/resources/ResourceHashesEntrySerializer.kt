/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.core.persistence.Serializer
import com.datadog.android.sessionreplay.model.ResourceHashesEntry

internal class ResourceHashesEntrySerializer : Serializer<ResourceHashesEntry> {
    override fun serialize(model: ResourceHashesEntry): String {
        return model.toJson().toString()
    }
}
