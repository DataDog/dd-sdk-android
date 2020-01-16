package com.datadog.android.utils.forge

import com.nhaarman.mockitokotlin2.mock
import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.api.Config
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import io.opentracing.Tracer

internal class SpanForgeryFactory : ForgeryFactory<DDSpan> {

    companion object {
        val TEST_TRACER = DDTracer(Config.get(), mock())
    }

    override fun getForgery(forge: Forge): DDSpan {
        val operationName = forge.anAlphabeticalString()
        val resourceName = forge.anAlphabeticalString()
        val serviceName = forge.anAlphabeticalString()
        val spanType = forge.anAlphabeticalString()
        val isWithErrorFlag = forge.aBool()
        val tags = forge.exhaustiveTraceTags()
        val spanBuilder = TEST_TRACER
            .buildSpan(operationName)
            .withSpanType(spanType)
            .withResourceName(resourceName)
            .withServiceName(serviceName)

        if (isWithErrorFlag) {
            spanBuilder.withErrorFlag()
        }
        (spanBuilder as Tracer.SpanBuilder).apply {
            tags.forEach {
                val mapValue = it.value
                when (mapValue) {
                    is String -> this.withTag(it.key, mapValue)
                    is Number -> this.withTag(it.key, mapValue)
                    is Boolean -> this.withTag(it.key, mapValue)
                }
            }
        }
        return spanBuilder.start()
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
}
