/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.core.propagation

import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation
import com.datadog.trace.core.DDSpanContext
import com.datadog.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class W3CHttpCodecTest {

    @Mock
    lateinit var mockContext: DDSpanContext

    @Mock
    lateinit var mockCarrier: Any

    @Mock
    lateinit var mockSetter: AgentPropagation.Setter<Any>

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockContext.traceId).thenReturn(DDTraceId.from(forge.aLong(min = 0L)))
        whenever(mockContext.propagationTags).thenReturn(mock())
    }

    @Test
    fun `M add baggage W inject { sessionId is in context }`(
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val injector = W3CHttpCodec.newInjector(emptyMap<String, String>())
        whenever(mockContext.tags).thenReturn(mapOf(HttpCodec.RUM_SESSION_ID_KEY to fakeSessionId))

        // When
        injector.inject(mockContext, mockCarrier, mockSetter)

        // Then
        argumentCaptor<String> {
            verify(mockSetter).set(
                eq(mockCarrier),
                eq(W3CHttpCodec.BAGGAGE_KEY),
                capture()
            )

            assertThat(firstValue).isEqualTo("session.id=$fakeSessionId")
        }
    }
}
