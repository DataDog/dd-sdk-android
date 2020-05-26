/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.rum.RumResourceType

internal sealed class RumEventData(val category: String) {

    internal data class Resource(
        val type: RumResourceType,
        val method: String,
        val url: String,
        val durationNanoSeconds: Long,
        val timing: Timing?
    ) : RumEventData("resource") {

        internal data class Timing(
            val dnsStart: Long,
            val dnsDuration: Long,
            val connectStart: Long,
            val connectDuration: Long,
            val sslStart: Long,
            val sslDuration: Long,
            val firstByteStart: Long,
            val firstByteDuration: Long,
            val downloadStart: Long,
            val downloadDuration: Long
        )
    }

    internal data class Action(
        val type: String,
        val id: String,
        val durationNanoSeconds: Long,
        val errorCount: Int = 0,
        val resourceCount: Int = 0
    ) : RumEventData("action")

    internal data class View(
        val name: String,
        val durationNanoSeconds: Long,
        val errorCount: Int = 0,
        val resourceCount: Int = 0,
        val actionCount: Int = 0,
        val version: Int = 1
    ) : RumEventData("view") {

        fun incrementErrorCount(): View {
            return copy(errorCount = errorCount + 1)
        }

        fun incrementResourceCount(): View {
            return copy(resourceCount = resourceCount + 1)
        }

        fun incrementActionCount(): View {
            return copy(actionCount = actionCount + 1)
        }
    }

    internal data class Error(
        val message: String,
        val source: String,
        val throwable: Throwable? = null
    ) : RumEventData("error")
}
