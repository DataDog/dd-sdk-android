/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

import java.util.Collections

/**
 * Standard event details. Collections are defensively copied and immutable.
 *
 * @property providerName the implementation name, not the client instance or targeting key.
 * @param flagsChanged optional known affected keys; omission does not imply a complete diff.
 * @property message optional information about the event.
 * @property errorCode optional standard event error classification.
 * @param eventMetadata optional primitive metadata. Installation notifications leave this empty.
 */
@Suppress("UnsafeThirdPartyFunctionCall") // Non-null inputs are copied into privately owned collections.
class FlagsClientEventDetails @JvmOverloads constructor(
    val providerName: String,
    flagsChanged: List<String>? = null,
    val message: String? = null,
    val errorCode: FlagsEventErrorCode? = null,
    eventMetadata: Map<String, FlagsEventMetadataValue> = emptyMap()
) {
    /** Known affected keys, if supplied by the producer. */
    val flagsChanged: List<String>? = flagsChanged?.let { Collections.unmodifiableList(ArrayList(it)) }

    /** Optional metadata; no Datadog-specific keys are added. */
    val eventMetadata: Map<String, FlagsEventMetadataValue> = Collections.unmodifiableMap(HashMap(eventMetadata))
}
