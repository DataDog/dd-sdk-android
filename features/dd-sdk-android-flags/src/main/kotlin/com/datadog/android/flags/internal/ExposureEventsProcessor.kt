/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.internal.model.ExposureEvent
import com.datadog.android.flags.internal.model.Identifier
import com.datadog.android.flags.internal.model.Subject
import com.datadog.android.flags.internal.storage.RecordWriter
import java.util.concurrent.ConcurrentHashMap

internal class ExposureEventsProcessor(
    private val writer: RecordWriter
) : EventsProcessor {

    private data class CacheKey(
        val targetingKey: String,
        val flagName: String,
        val allocationKey: String,
        val variationKey: String
    )

    private val exposuresSentCache = ConcurrentHashMap<CacheKey, Unit>()

    override fun processEvent(flagName: String, context: EvaluationContext, data: PrecomputedFlag) {
        val cacheKey = CacheKey(
            targetingKey = context.targetingKey,
            flagName = flagName,
            allocationKey = data.allocationKey,
            variationKey = data.variationKey
        )

        @Suppress("UnsafeThirdPartyFunctionCall") // cache key cannot be null
        if (exposuresSentCache.putIfAbsent(cacheKey, Unit) == null) {
            val event = buildExposureEvent(flagName, context, data)
            writeExposureEvent(event)
        }
    }

    override fun clearExposureCache() {
        exposuresSentCache.clear()
    }

    private fun buildExposureEvent(
        flagName: String,
        context: EvaluationContext,
        data: PrecomputedFlag
    ): ExposureEvent {
        val now = System.currentTimeMillis()
        return ExposureEvent(
            timeStamp = now,
            allocation = Identifier(data.allocationKey),
            flag = Identifier(flagName),
            variant = Identifier(data.variationKey),
            subject = Subject(
                id = context.targetingKey,
                attributes = context.attributes.mapValues { it.value.toString() }
            )
        )
    }

    private fun writeExposureEvent(record: ExposureEvent) {
        writer.write(record)
    }
}
