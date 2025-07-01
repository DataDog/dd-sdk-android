/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace

import com.datadog.android.trace.api.constants.DatadogTracingConstants.DEFAULT_ASYNC_PROPAGATING
import com.datadog.android.trace.api.constants.DatadogTracingConstants.ErrorPriorities
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.opentelemetry.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class OtelSpanTest {

    @Mock
    lateinit var mockAgentTracer: DatadogTracer

    @Mock
    lateinit var mockAgentSpan: DatadogSpan
    lateinit var testedSpan: OtelSpan

    @BeforeEach
    fun `set up`() {
        testedSpan = OtelSpan(mockAgentSpan, mockAgentTracer)
    }

    // region Init
    @Test
    fun `M set default attributes W init`() {
        // Then
        assertThat(testedSpan.isRecording).isTrue()
        assertThat(testedSpan.statusCode).isEqualTo(StatusCode.UNSET)
    }

    @Test
    fun `M return NOOP W invalid()`() {
        // When
        val result = OtelSpan.invalid()

        // Then
        assertThat(result).isSameAs(OtelSpan.NoopSpan.INSTANCE)
    }

    // endregion

    // region setAttribute

    @Test
    fun `M add attribute W setAttribute`() {
        // Given
        val expectedTags = listOf(
            "string" to "b",
            "empty_string" to "",
            "number" to 2L,
            "boolean" to false,
            "empty-string-attribute" to "",
            "string-array.0" to "d",
            "string-array.1" to "e",
            "string-array.2" to "f",
            "boolean-array.0" to false,
            "boolean-array.1" to true,
            "long-array.0" to 5L,
            "long-array.1" to 6L,
            "long-array.2" to 7L,
            "long-array.3" to 8L,
            "double-array.0" to 34.0,
            "double-array.1" to 5.67,
            "empty-array" to ""
        )

        // When
        testedSpan.setAttribute("string", "b")
        testedSpan.setAttribute("empty_string", "")
        testedSpan.setAttribute("number", 2L)
        testedSpan.setAttribute("boolean", false)
        testedSpan.setAttribute(AttributeKey.stringKey("null-string-attribute"), null)
        testedSpan.setAttribute(AttributeKey.stringKey("empty-string-attribute"), "")
        testedSpan.setAttribute(AttributeKey.stringArrayKey("string-array"), listOf("d", "e", "f"))
        testedSpan.setAttribute(AttributeKey.booleanArrayKey("boolean-array"), listOf(false, true))
        testedSpan.setAttribute(AttributeKey.longArrayKey("long-array"), listOf(5L, 6L, 7L, 8L))
        testedSpan.setAttribute(AttributeKey.doubleArrayKey("double-array"), listOf(34.0, 5.67))
        testedSpan.setAttribute(AttributeKey.stringArrayKey("empty-array"), emptyList())
        testedSpan.setAttribute(AttributeKey.stringArrayKey("null-array"), null)

        // Then
        expectedTags.forEach {
            verify(mockAgentSpan).setTag(it.first, it.second)
        }
    }

    // endregion

    // region setStatus

    @ParameterizedTest
    @EnumSource(StatusCode::class)
    fun `M delegate to AgentSpan W setStatus { statusCode not set }`(
        fakeStatusCode: StatusCode,
        @StringForgery fakeDescription: String
    ) {
        // Given
        val expectedIsError = fakeStatusCode == StatusCode.ERROR
        val expectedErrorMessage = if (expectedIsError) fakeDescription else null

        // When
        testedSpan.setStatus(fakeStatusCode, fakeDescription)

        // Then
        verify(mockAgentSpan).isError = expectedIsError
        verify(mockAgentSpan).setErrorMessage(expectedErrorMessage)
        assertThat(testedSpan.statusCode).isEqualTo(fakeStatusCode)
    }

    @ParameterizedTest
    @EnumSource(StatusCode::class)
    fun `M do nothing W setStatus { isRecording is false }`(
        fakeStatusCode: StatusCode,
        @StringForgery fakeDescription: String
    ) {
        // Given
        testedSpan.end()

        // When
        testedSpan.setStatus(fakeStatusCode, fakeDescription)

        // Then
        verify(mockAgentSpan, never()).isError = any()
        verify(mockAgentSpan, never()).setErrorMessage(anyOrNull())
        assertThat(testedSpan.statusCode).isEqualTo(StatusCode.UNSET)
    }

    @Test
    fun `M delegate to AgentSpan W setStatus { statusCode Error to Ok }`(
        @StringForgery fakeDescription1: String,
        @StringForgery fakeDescription2: String
    ) {
        // Given
        testedSpan.setStatus(StatusCode.ERROR, fakeDescription1)

        // When
        testedSpan.setStatus(StatusCode.OK, fakeDescription2)

        // Then
        verify(mockAgentSpan).isError = false
        verify(mockAgentSpan).setErrorMessage(null)
        assertThat(testedSpan.statusCode).isEqualTo(StatusCode.ERROR)
    }

    @ParameterizedTest
    @MethodSource("errorStatuses")
    fun `M do nothing W setStatus { status code Any to Error or UNSET }`(
        fakeStatusCode1: StatusCode,
        fakeStatusCode2: StatusCode,
        @StringForgery fakeDescription1: String,
        @StringForgery fakeDescription2: String
    ) {
        // Given
        val expectedIsError = fakeStatusCode1 == StatusCode.ERROR
        val expectedErrorMessage = if (expectedIsError) fakeDescription1 else null
        testedSpan.setStatus(fakeStatusCode1, fakeDescription1)

        // When
        testedSpan.setStatus(fakeStatusCode2, fakeDescription2)

        // Then
        verify(mockAgentSpan).isError = expectedIsError
        verify(mockAgentSpan).setErrorMessage(expectedErrorMessage)
        assertThat(testedSpan.statusCode).isEqualTo(fakeStatusCode1)
        verifyNoMoreInteractions(mockAgentSpan)
    }

    // endregion

    // region recordException

    @Test
    fun `M delegate to AgentSpan W recordException`(@Forgery fakeThrowable: Throwable) {
        // Given
        val mockAttributes: Attributes = mock()

        // When
        testedSpan.recordException(fakeThrowable, mockAttributes)

        // Then
        verify(mockAgentSpan).addThrowable(fakeThrowable, ErrorPriorities.UNSET)
        verifyNoInteractions(mockAttributes)
    }

    @Test
    fun `M do nothing W recordException { isRecording is false }`(@Forgery fakeThrowable: Throwable) {
        // Given
        testedSpan.end()
        val mockAttributes: Attributes = mock()

        // When
        testedSpan.recordException(fakeThrowable, mockAttributes)

        // Then
        verify(mockAgentSpan, never()).addThrowable(fakeThrowable, ErrorPriorities.UNSET)
        verifyNoInteractions(mockAttributes)
    }

    // endregion

    // region activate

    @Test
    fun `M delegate to AgentSpan W activate`() {
        // When
        testedSpan.activate()

        // Then
        verify(mockAgentTracer).activateSpan(
            mockAgentSpan,
            DEFAULT_ASYNC_PROPAGATING
        )
    }

    // endregion

    companion object {
        @JvmStatic
        fun errorStatuses(): List<Arguments> {
            return listOf(
                Arguments.of(StatusCode.ERROR, StatusCode.ERROR),
                Arguments.of(StatusCode.ERROR, StatusCode.UNSET),
                Arguments.of(StatusCode.OK, StatusCode.ERROR),
                Arguments.of(StatusCode.OK, StatusCode.UNSET),
                Arguments.of(StatusCode.OK, StatusCode.OK)
            )
        }
    }
}
