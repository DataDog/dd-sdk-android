/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import java.io.Serializable

// An individual resource. It is used to describe binary representation of heavy resources such as images.
internal data class SessionReplayResource(
    var identifier: String,
    var data: ByteArray,
    var context: SessionReplayResourceContext
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SessionReplayResource

        if (identifier != other.identifier) return false
        if (!data.contentEquals(other.data)) return false
        return context == other.context
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + identifier.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + context.hashCode()
        return result
    }

    internal companion object {
        private const val serialVersionUID: Long = 1L
    }
}
