/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Read-only description of a Flags client event.
 *
 * This value does not emit events or change client state. Key order and duplicates are preserved.
 * Events are constructed internally by the SDK and supplied to registered listeners.
 *
 * @property type the event kind.
 * @param flagsChanged optional supplied keys; null means absent and an empty list means explicitly empty.
 */
class FlagsClientEvent internal constructor(
    val type: FlagsClientEventType,
    flagsChanged: List<String>? = null
) {
    /** Snapshot of supplied keys, or null when keys were not supplied. */
    val flagsChanged: List<String>? = flagsChanged?.toList()

    override fun toString(): String =
        "FlagsClientEvent(type=$type, flagsChanged=$flagsChanged)"
}
