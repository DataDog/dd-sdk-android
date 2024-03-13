/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.trace.api.DDTraceId
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.DDSpanContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CoreDDSpanForgeryFactory : ForgeryFactory<DDSpan> {

    override fun getForgery(forge: Forge): DDSpan {
        val baggageItems = forge.exhaustiveMeta()
        val tags = forge.exhaustiveTraceTags()
        val metrics = forge.exhaustiveMetrics()
        val operationName = forge.anAlphabeticalString()
        val resourceName = forge.anAlphabeticalString()
        val serviceName = forge.anAlphabeticalString()
        val spanType = forge.anAlphabeticalString()
        val spanStartTime = forge.aPositiveLong()
        val spanDuration = forge.aPositiveLong()
        val traceId = forge.aLong(min = 1)
        val spanId = forge.aLong(min = 1)
        val parentId = forge.aLong(min = 1)
        val samplingPriority = forge.anInt()
        val tagsAndMetrics = tags + metrics
        val mockSpanContext: DDSpanContext = mock {
            whenever(it.baggageItems).thenReturn(baggageItems)
            whenever(it.tags).thenReturn(tagsAndMetrics)
        }
        val mockDDSpan: DDSpan = mock {
            whenever(it.context()).thenReturn(mockSpanContext)
            whenever(it.serviceName).thenReturn(serviceName)
            whenever(it.operationName).thenReturn(operationName)
            whenever(it.resourceName).thenReturn(resourceName)
            whenever(it.spanType).thenReturn(spanType)
            whenever(it.error).thenReturn(forge.anInt())
            whenever(it.startTime).thenReturn(spanStartTime)
            whenever(it.durationNano).thenReturn(spanDuration)
            whenever(it.spanId).thenReturn(spanId)
            whenever(it.traceId).thenReturn(DDTraceId.from(traceId))
            whenever(it.parentId).thenReturn(parentId)
            whenever(it.baggage).thenReturn(baggageItems)
            whenever(it.tags).thenReturn(tagsAndMetrics)
            whenever(it.samplingPriority()).thenReturn(samplingPriority)
        }
        return mockDDSpan
    }

    private fun Forge.exhaustiveTraceTags(): Map<String, Any> {
        return listOf(
            aBool(),
            anInt(),
            aLong(),
            aFloat(),
            aDouble(),
            anAsciiString()
        ).map { anAlphabeticalString() to it }
            .toMap()
    }

    private fun Forge.exhaustiveMetrics(): Map<String, Number> {
        return listOf(
            aLong(),
            anInt(),
            aFloat(),
            aDouble()
        ).associate { anAlphabeticalString() to it as Number }
    }

    private fun Forge.exhaustiveMeta(): Map<String, String> {
        return listOf(
            aString()
        ).associateBy { anAlphabeticalString() }
    }
}
