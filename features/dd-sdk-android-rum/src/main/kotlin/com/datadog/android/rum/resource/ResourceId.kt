/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.resource

import java.util.UUID

/**
 * Describes a Resource being tracked in RUM.
 *
 * A resource will first try to be matched with the UUID if possible, and if missing will use the key property.
 * The UUID should be unique (based off of [UUID] class). If missing, because key could have duplicates,
 * this can lead to unpredictable behaviors and erroneous Resource timings.
 *
 * @param key a key to identify the resource
 * @param uuid a UUID based unique identifier (optional)
 */
class ResourceId(
    val key: String,
    val uuid: String?
) {

    val someOtherUUID: String = UUID.randomUUID().toString()

    override fun equals(other: Any?): Boolean {
        return if (other is ResourceId) {
            if (uuid.isNullOrBlank() || other.uuid.isNullOrBlank()) {
                key == other.key
            } else {
                (uuid == other.uuid) && (key == other.key)
            }
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}
