/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.serialization

import com.datadog.android.flags.internal.model.PrecomputedAssignments
import com.google.gson.JsonObject

/** Detached precomputed data; decoding does not initialize Flags or perform I/O. */
internal class PortableFlagsConfiguration(
    val precomputed: Precomputed?,
    val failure: Failure?
) {
    enum class Failure { INVALID_ENVELOPE, INVALID_PRECOMPUTED }

    class Precomputed(
        val response: String,
        context: JsonObject?,
        val fetchedAt: Double?,
        val etag: String?,
        val assignments: PrecomputedAssignments,
        val flagErrors: Map<String, String>
    ) {
        private val storedContext = context?.deepCopy()
        val context: JsonObject? get() = storedContext?.deepCopy()
    }
}
