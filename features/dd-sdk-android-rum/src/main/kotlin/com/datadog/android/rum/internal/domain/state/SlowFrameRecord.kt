/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.domain.state

internal data class SlowFrameRecord(
    val startTimestampNs: Long,
    var durationNs: Long
) {
    override fun toString(): String {
        return "${durationNs / NS_IN_MS}ms"
    }

    companion object {
        private const val NS_IN_MS = 1_000_000.0
    }
}
