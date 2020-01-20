package com.datadog.android.utils.forge

import com.nhaarman.mockitokotlin2.mock
import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.api.Config
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class SpanForgeryFactory : ForgeryFactory<DDSpan> {

    override fun getForgery(forge: Forge): DDSpan {
        val operationName = forge.anAlphabeticalString()
        val resourceName = forge.anAlphabeticalString()
        val serviceName = forge.anAlphabeticalString()
        val spanType = forge.anAlphabeticalString()
        val isWithErrorFlag = forge.aBool()
        val tags = forge.exhaustiveTraceTags()
        return generateSpanBuilder(
            operationName,
            spanType,
            resourceName,
            serviceName,
            isWithErrorFlag,
            tags
        ).start()
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

    companion object {
        val TEST_TRACER = DDTracer(Config.get(), mock())

        fun generateSpanBuilder(
            operationName: String,
            spanType: String,
            resourceName: String,
            serviceName: String,
            isWithErrorFlag: Boolean,
            tags: Map<String, Any>
        ): DDTracer.DDSpanBuilder {
            val spanBuilder = TEST_TRACER
                .buildSpan(operationName)
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
