/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpanLink
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class AgentSpanLinkForgeryFactory : ForgeryFactory<AgentSpanLink> {

    override fun getForgery(forge: Forge): AgentSpanLink {
        val traceId = DDTraceId.from(forge.aLong(min = 1))
        val spanId = forge.aLong(min = 1)
        val flags = forge.aTinyInt().toByte()
        val attributes: AgentSpanLink.Attributes = mock {
            whenever(it.asMap()).thenReturn(forge.spanLinkAttributes())
        }
        val traceState = forge.anAlphabeticalString()
        return mock {
            whenever(it.traceId()).doReturn(traceId)
            whenever(it.spanId()).doReturn(spanId)
            whenever(it.traceFlags()).doReturn(flags)
            whenever(it.attributes()).doReturn(attributes)
            whenever(it.traceState()).doReturn(traceState)
        }
    }

    private fun Forge.spanLinkAttributes(): Map<String, String> {
        return listOf(
            aString()
        ).associateByTo(mutableMapOf()) { anAlphabeticalString() }
    }
}
