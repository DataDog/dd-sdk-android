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
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogHttpCodecTest {

    @Mock
    lateinit var mockContext: DDSpanContext

    @Mock
    lateinit var mockCarrier: Any

    @Mock
    lateinit var mockSetter: AgentPropagation.Setter<Any>

    private val testedInjector = DatadogHttpCodec.newInjector(emptyMap<String, String>())

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockContext.traceId).thenReturn(DDTraceId.from(forge.aLong(min = 0L)))
        whenever(mockContext.propagationTags).thenReturn(mock())
    }

    @ParameterizedTest
    @MethodSource("rumContext")
    fun `M add baggage W inject { rum context present }`(
        userId: String?,
        accountId: String?,
        sessionId: String?,
        expected: String
    ) {
        // Given
        whenever(mockContext.tags).thenReturn(
            mapOf(
                HttpCodec.RUM_KEY_USER_ID to userId,
                HttpCodec.RUM_KEY_ACCOUNT_ID to accountId,
                HttpCodec.RUM_KEY_SESSION_ID to sessionId
            )
        )

        // When
        testedInjector.inject(mockContext, mockCarrier, mockSetter)

        // Then
        argumentCaptor<String> {
            verify(mockSetter).set(
                eq(mockCarrier),
                eq(W3CHttpCodec.BAGGAGE_KEY),
                capture()
            )

            assertThat(firstValue).isEqualTo(expected)
        }
    }

    @Test
    fun `M not add  baggage W inject { no rum context }`() {
        // Given
        whenever(mockContext.tags).thenReturn(emptyMap())

        // When
        testedInjector.inject(mockContext, mockCarrier, mockSetter)

        // Then
        argumentCaptor<String> {
            verify(mockSetter, never()).set(
                eq(mockCarrier),
                eq(W3CHttpCodec.BAGGAGE_KEY),
                capture()
            )
        }
    }

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun rumContext() = listOf(
            Arguments.of(
                "userId",
                "accountId",
                "sessionId",
                "account.id=accountId,user.id=userId,session.id=sessionId"
            ),
            Arguments.of(
                null,
                "accountId",
                "sessionId",
                "account.id=accountId,session.id=sessionId"
            ),
            Arguments.of(
                "userId",
                null,
                "sessionId",
                "user.id=userId,session.id=sessionId"
            ),
            Arguments.of(
                "userId",
                "accountId",
                null,
                "user.id=userId,account.id=accountId"
            )
        )
    }
}
