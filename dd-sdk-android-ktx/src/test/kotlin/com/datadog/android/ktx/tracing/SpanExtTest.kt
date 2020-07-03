package com.datadog.android.ktx.tracing

import com.datadog.android.ktx.utils.Configurator
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.log.Fields
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class SpanExtTest {

    @AfterEach
    fun `tear down`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @Test
    fun `set error throwable on Span`(
        @Forgery throwable: Throwable
    ) {
        val mockSpan: Span = mock()

        mockSpan.setError(throwable)

        argumentCaptor<Map<String, Any>>().apply {
            verify(mockSpan).log(capture())
            assertThat(firstValue)
                .containsEntry(Fields.ERROR_OBJECT, throwable)
                .containsOnlyKeys(Fields.ERROR_OBJECT)
        }
    }

    @Test
    fun `set error message on Span`(
        @StringForgery(StringForgeryType.ALPHABETICAL) message: String
    ) {
        val mockSpan: Span = mock()

        mockSpan.setError(message)

        argumentCaptor<Map<String, Any>>().apply {
            verify(mockSpan).log(capture())
            assertThat(firstValue)
                .containsEntry(Fields.MESSAGE, message)
                .containsOnlyKeys(Fields.MESSAGE)
        }
    }

    @Test
    fun `create span around lambda`(
        @StringForgery(StringForgeryType.ALPHABETICAL) operationName: String,
        @LongForgery result: Long
    ) {
        var lambdaCalled = false
        val mockTracer: Tracer = mock()
        val mockSpanBuilder: Tracer.SpanBuilder = mock()
        val mockSpan: Span = mock()
        val mockScope: Scope = mock()
        GlobalTracer.registerIfAbsent(mockTracer)
        whenever(mockTracer.buildSpan(operationName)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan
        whenever(mockTracer.activateSpan(mockSpan)).thenReturn(mockScope)

        val callResult = withinSpan(operationName) {
            lambdaCalled = true
            result
        }

        assertThat(lambdaCalled).isTrue()
        assertThat(callResult).isEqualTo(result)
        verify(mockSpan).finish()
        verify(mockScope).close()
    }

    @Test
    fun `create span around lambda with parent`(
        @StringForgery(StringForgeryType.ALPHABETICAL) operationName: String,
        @LongForgery result: Long
    ) {
        var lambdaCalled = false
        val mockTracer: Tracer = mock()
        val mockSpanBuilder: Tracer.SpanBuilder = mock()
        val mockSpan: Span = mock()
        val mockScope: Scope = mock()
        val mockParentSpan: Span = mock()
        GlobalTracer.registerIfAbsent(mockTracer)
        whenever(mockTracer.buildSpan(operationName)) doReturn mockSpanBuilder
        whenever(mockTracer.activateSpan(mockSpan)) doReturn mockScope
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan

        val callResult = withinSpan(operationName, mockParentSpan) {
            lambdaCalled = true
            result
        }

        assertThat(lambdaCalled).isTrue()
        assertThat(callResult).isEqualTo(result)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `create span around lambda with error`(
        @StringForgery(StringForgeryType.ALPHABETICAL) operationName: String,
        @Forgery throwable: Throwable,
        @LongForgery result: Long
    ) {
        var lambdaCalled = false
        val mockTracer: Tracer = mock()
        val mockSpanBuilder: Tracer.SpanBuilder = mock()
        val mockSpan: Span = mock()
        val mockScope: Scope = mock()
        val mockParentSpan: Span = mock()
        GlobalTracer.registerIfAbsent(mockTracer)
        whenever(mockTracer.buildSpan(operationName)) doReturn mockSpanBuilder
        whenever(mockTracer.activateSpan(mockSpan)) doReturn mockScope
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan

        assertThrows<Throwable> {
            withinSpan(operationName, mockParentSpan) {
                lambdaCalled = true
                throw throwable
            }
        }

        assertThat(lambdaCalled).isTrue()

        inOrder(mockSpan, mockScope) {
            argumentCaptor<Map<String, Any>>().apply {
                verify(mockSpan).log(capture())
                assertThat(firstValue)
                    .containsEntry(Fields.ERROR_OBJECT, throwable)
                    .containsOnlyKeys(Fields.ERROR_OBJECT)
            }

            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }
}
