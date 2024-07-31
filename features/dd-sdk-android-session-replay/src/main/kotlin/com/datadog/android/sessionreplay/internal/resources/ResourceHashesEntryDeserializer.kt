/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.sessionreplay.model.ResourceHashesEntry
import com.google.gson.JsonParseException
import java.util.Locale

internal class ResourceHashesEntryDeserializer(
    private val internalLogger: InternalLogger
) : Deserializer<String, ResourceHashesEntry> {
    override fun deserialize(model: String): ResourceHashesEntry? {
        return try {
            ResourceHashesEntry.fromJson(model)
        } catch (e: JsonParseException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                {
                    DESERIALIZE_ERROR_MESSAGE_FORMAT.format(
                        Locale.US,
                        model
                    )
                }
            )
            null
        }
    }

    internal companion object {
        internal const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the ResourceHashesEntry: %s"
    }
}
