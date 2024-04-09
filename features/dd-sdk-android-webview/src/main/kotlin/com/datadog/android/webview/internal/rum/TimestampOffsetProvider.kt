/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext

internal class TimestampOffsetProvider(private val internalLogger: InternalLogger) {

    internal val offsets: LinkedHashMap<String, Long> = LinkedHashMap()

    @Synchronized
    internal fun getOffset(viewId: String, datadogContext: DatadogContext): Long {
        var offset = offsets[viewId]
        if (offset == null) {
            offset = datadogContext.time.serverTimeOffsetMs
            offsets[viewId] = offset
        }
        purgeOffsets()
        return offset
    }

    private fun purgeOffsets() {
        while (offsets.entries.size > MAX_VIEW_TIME_OFFSETS_RETAIN) {
            try {
                val viewId = offsets.entries.first()
                offsets.remove(viewId.key)
            } catch (e: NoSuchElementException) {
                // it should not happen but just in case.
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    { "Trying to remove offset from an empty map." },
                    e
                )
                break
            }
        }
    }

    companion object {
        const val MAX_VIEW_TIME_OFFSETS_RETAIN = 3
    }
}
