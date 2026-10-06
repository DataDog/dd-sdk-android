/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

import java.util.Collections

/**
 * Immutable description of a Flags client event.
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
    // Kotlin List is read-only, but Java callers and casts can still mutate an ordinary copy.
    // ArrayList and unmodifiableList reject null inputs; let supplies non-null keys and the copy is non-null.
    @Suppress("UnsafeThirdPartyFunctionCall")
    val flagsChanged: List<String>? = flagsChanged?.let { Collections.unmodifiableList(ArrayList(it)) }

    override fun toString(): String =
        "FlagsClientEvent(type=$type, flagsChanged=$flagsChanged)"
}
