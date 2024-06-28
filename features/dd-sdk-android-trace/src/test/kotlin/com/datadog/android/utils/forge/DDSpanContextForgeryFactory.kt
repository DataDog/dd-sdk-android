/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.opentracing.DDSpanContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock
import java.math.BigInteger

internal class DDSpanContextForgeryFactory : ForgeryFactory<DDSpanContext> {

    override fun getForgery(forge: Forge): DDSpanContext {
        val traceIdAsHexa = forge.aStringMatching("[0-9a-f]{32}")
        val traceId = BigInteger(traceIdAsHexa, 16)
        val spanIdAsHexa = forge.aStringMatching("[0-9a-f]{16}")
        val spanId = BigInteger(spanIdAsHexa, 16)
        val parentSpanIdAsHexa = forge.aStringMatching("[0-9a-f]{16}")
        val parentId = BigInteger(parentSpanIdAsHexa, 16)
        val operationName = forge.anAlphabeticalString()
        val resourceName = forge.anAlphabeticalString()
        val serviceName = forge.anAlphabeticalString()
        val spanType = forge.anAlphabeticalString()
        val origin = forge.anAlphabeticalString()
        val samplingPriority = forge.anInt()
        val baggageItems = forge.aMap(size = forge.anInt(min = 0, max = 10)) {
            anAlphabeticalString() to anAlphabeticalString()
        }

        return DDSpanContext(
            traceId,
            spanId,
            parentId,
            serviceName,
            operationName,
            resourceName,
            samplingPriority,
            origin,
            baggageItems,
            forge.aBool(),
            spanType,
            emptyMap(),
            mock(),
            mock(),
            mock()
        )
    }
}
