/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.legacy.trace.api.Config
import com.datadog.legacy.trace.api.sampling.PrioritySampling
import com.datadog.opentracing.DDSpan
import com.datadog.opentracing.DDTracer
import com.datadog.opentracing.DDTracer.DDSpanBuilder
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock
import java.security.SecureRandom

internal class SpanForgeryFactory(
    private val forgeSamplingPriority: (forge: Forge, span: DDSpan) -> Unit = ::forgeSamplingPriorityDefault
) : ForgeryFactory<DDSpan> {

    override fun getForgery(forge: Forge): DDSpan {
        val operationName = forge.anAlphabeticalString()
        val resourceName = forge.anAlphabeticalString()
        val serviceName = forge.anAlphabeticalString()
        val spanType = forge.anAlphabeticalString()
        val isWithErrorFlag = forge.aBool()
        val tags = forge.exhaustiveTraceTags()
        val metrics = forge.exhaustiveMetrics()
        val meta = forge.exhaustiveMeta()
        val span = generateSpanBuilder(
            operationName,
            spanType,
            resourceName,
            serviceName,
            isWithErrorFlag,
            tags
        ).start() as DDSpan

        forgeSamplingPriority(forge, span)

        metrics.forEach {
            span.context().setMetric(it.key, it.value)
        }
        meta.forEach {
            span.context().baggageItems.putIfAbsent(it.key, it.value)
        }
        return span
    }

    fun Forge.exhaustiveTraceTags(): Map<String, Any> {
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

    fun Forge.exhaustiveMetrics(): Map<String, Number> {
        return listOf(
            aLong(),
            anInt(),
            aFloat(),
            aDouble()
        ).map { anAlphabeticalString() to it as Number }
            .toMap()
    }

    fun Forge.exhaustiveMeta(): Map<String, String> {
        return listOf(
            aString()
        ).map { anAlphabeticalString() to it }
            .toMap()
    }

    companion object {
        // TODO remove service call once Tracer gets default value
        val TEST_TRACER = object : DDTracer(Config.get(), mock(), SecureRandom()) {
        }

        fun generateSpanBuilder(
            operationName: String,
            spanType: String,
            resourceName: String,
            serviceName: String,
            isWithErrorFlag: Boolean,
            tags: Map<String, Any>
        ): DDTracer.DDSpanBuilder {
            val spanBuilder = (TEST_TRACER.buildSpan(operationName) as DDSpanBuilder)
                .withSpanType(spanType)
                .withResourceName(resourceName)
                .withServiceName(serviceName)

            if (isWithErrorFlag) {
                spanBuilder.withErrorFlag()
            }

            tags.forEach {
                val mapValue = it.value
                when (mapValue) {
                    is String -> spanBuilder.withTag(it.key, mapValue)
                    is Number -> spanBuilder.withTag(it.key, mapValue)
                    is Boolean -> spanBuilder.withTag(it.key, mapValue)
                }
            }

            return spanBuilder
        }
    }
}

private fun forgeSamplingPriorityDefault(forge: Forge, span: DDSpan) {
    if (forge.aBool()) {
        span.samplingPriority = forge.anElementFrom(
            PrioritySampling.UNSET,
            PrioritySampling.SAMPLER_DROP,
            PrioritySampling.USER_DROP,
            PrioritySampling.SAMPLER_KEEP,
            PrioritySampling.USER_KEEP
        )
    }
}
