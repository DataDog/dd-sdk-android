/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.core.internal.persistence.Deserializer

internal class ResourceHashesEntryDeserializer : Deserializer<String, ResourceHashesEntry> {
    override fun deserialize(model: String): ResourceHashesEntry? {
        val parts = model.split("|")
        val lastUpdateDate = stringToLong(parts[0]) ?: return null
        return ResourceHashesEntry(
            lastUpdateDateNs = lastUpdateDate,
            resourceHashes = stringToSet(parts[1])
        )
    }

    private fun stringToLong(input: String): Long? {
        return try {
            input.toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun stringToSet(input: String): Set<String> {
        return input.split(",").toSet()
    }
}
