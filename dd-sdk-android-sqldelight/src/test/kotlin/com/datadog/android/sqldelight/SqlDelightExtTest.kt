/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqldelight

import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import com.squareup.sqldelight.Transacter
import com.squareup.sqldelight.TransactionWithReturn
import com.squareup.sqldelight.TransactionWithoutReturn
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.log.Fields
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SqlDelightExtTest {
    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockSpanBuilder: Tracer.SpanBuilder

    @Mock
    lateinit var mockSpan: Span

    @Mock
    lateinit var mockParentSpan: Span

    @Mock
    lateinit var mockScope: Scope

    @StringForgery
    lateinit var fakeOperationName: String

    @Mock
    lateinit var mockTransacter: Transacter

    @Forgery
    lateinit var fakeException: Throwable

    @Mock
    lateinit var mockTransactionWithReturn: TransactionWithReturn<Any>

    @Mock
    lateinit var mockTransactionWithoutReturn: TransactionWithoutReturn

    @BeforeEach
    fun `set up`() {
        GlobalTracer.registerIfAbsent(mockTracer)
        whenever(mockTracer.buildSpan(fakeOperationName)) doReturn mockSpanBuilder
        whenever(mockTracer.activateSpan(mockSpan)) doReturn mockScope
        whenever(mockSpanBuilder.start()) doReturn mockSpan
        doAnswer {
            (it.arguments[1] as TransactionWithoutReturn.() -> Unit).invoke(
                mockTransactionWithoutReturn
            )
        }.whenever(mockTransacter).transaction(any(), any())
        doAnswer {
            (it.arguments[1] as TransactionWithReturn<Any>.() -> Boolean).invoke(
                mockTransactionWithReturn
            )
        }.whenever(mockTransacter)
            .transactionWithResult(any(), any<TransactionWithReturn<Any>.() -> Any>())
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @Test
    fun `M create Span around transaction W transactionTraced() {nonEnclosing = true}`() {
        // GIVEN
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder
        var transactionExecuted = false
        val body: TransactionWithoutReturn.() -> Unit = {
            transactionExecuted = true
        }

        // WHEN
        mockTransacter.transactionTraced(fakeOperationName, true, body)

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpanBuilder).asChildOf(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
        verify(mockTransacter).transaction(eq(true), any())
        verifyNoMoreInteractions(mockTransacter)
    }

    @Test
    fun `M create Span around transaction W transactionTraced() {nonEnclosing = false}`() {
        // GIVEN
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder
        var transactionExecuted = false
        val body: TransactionWithoutReturn.() -> Unit = {
            transactionExecuted = true
        }

        // WHEN
        mockTransacter.transactionTraced(fakeOperationName, false, body)

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpanBuilder).asChildOf(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
        verify(mockTransacter).transaction(eq(false), any())
        verifyNoMoreInteractions(mockTransacter)
    }

    @Test
    fun `M create Span around transaction W transactionTraced() without parents`(forge: Forge) {
        // GIVEN
        val fakeNoEnclosing = forge.aBool()
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder
        var transactionExecuted = false
        val body: TransactionWithSpanAndWithoutReturn.() -> Unit = {
            transactionExecuted = true
        }

        // WHEN
        mockTransacter.transactionTraced(fakeOperationName, fakeNoEnclosing, body)

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpanBuilder).asChildOf(null as Span?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
        verify(mockTransacter).transaction(eq(fakeNoEnclosing), any())
        verifyNoMoreInteractions(mockTransacter)
    }

    @Test
    fun `M close the Span around transaction W transactionTraced() throws exception`(forge: Forge) {
        // GIVEN
        val fakeNoEnclosing = forge.aBool()
        var caughtException: Throwable? = null
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder

        // WHEN
        try {
            mockTransacter.transactionTraced(fakeOperationName, fakeNoEnclosing) {
                throw fakeException
            }
        } catch (e: Throwable) {
            caughtException = fakeException
        }

        // THEN
        assertThat(caughtException).isEqualTo(fakeException)
        verify(mockSpanBuilder).asChildOf(null as Span?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).log(
                argThat<Map<String, Any?>> {
                    this[Fields.ERROR_OBJECT] == fakeException
                }
            )
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M log the exception W transactionTraced() throws exception`(forge: Forge) {
        // GIVEN
        val fakeNoEnclosing = forge.aBool()
        var caughtException: Throwable? = null
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder

        // WHEN
        try {
            mockTransacter.transactionTraced(fakeOperationName, fakeNoEnclosing) {
                throw fakeException
            }
        } catch (e: Throwable) {
            caughtException = fakeException
        }

        // THEN
        assertThat(caughtException).isEqualTo(fakeException)
        verify(mockSpanBuilder).asChildOf(null as Span?)
        verify(mockSpan).log(
            argThat<Map<String, Any?>> {
                this[Fields.ERROR_OBJECT] == fakeException
            }
        )
    }

    @Test
    fun `M execute the transaction W transactionTraced()`(forge: Forge) {
        // GIVEN
        val fakeNoEnclosing = forge.aBool()
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder
        var transactionExecuted = false
        val afterCommitLambda = {
        }
        val body: TransactionWithSpanAndWithoutReturn.() -> Unit = {
            transactionExecuted = true
            afterCommit(afterCommitLambda)
        }

        // WHEN
        mockTransacter.transactionTraced(fakeOperationName, fakeNoEnclosing, body)

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockTransactionWithoutReturn).afterCommit(afterCommitLambda)
    }

    @Test
    fun `M add Span tags W transactionTraced()`(forge: Forge) {
        // GIVEN
        val fakeTagKey = forge.anAlphabeticalString()
        val fakeTagValue = forge.anAlphabeticalString()
        val fakeNoEnclosing = forge.aBool()
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder
        var transactionExecuted = false
        val body: TransactionWithSpanAndWithoutReturn.() -> Unit = {
            transactionExecuted = true
            setTag(fakeTagKey, fakeTagValue)
        }

        // WHEN
        mockTransacter.transactionTraced(fakeOperationName, fakeNoEnclosing, body)

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpan).setTag(fakeTagKey, fakeTagValue)
    }

    @Test
    fun `M create Span around W transactionTracedWithResult() {noEnclosing = true}`() {
        // // GIVEN
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder
        var transactionExecuted = false
        val body: TransactionWithSpanAndWithReturn<Boolean>.() -> Boolean = {
            true
        }

        // WHEN
        transactionExecuted =
            mockTransacter.transactionTracedWithResult(fakeOperationName, true, body)

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpanBuilder).asChildOf(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
        verify(mockTransacter).transactionWithResult(
            eq(true),
            any<TransactionWithReturn<Boolean>.() -> Boolean>()
        )
        verifyNoMoreInteractions(mockTransacter)
    }

    @Test
    fun `M create Span around W transactionTracedWithResult() {noEnclosing = false}`() {
        // GIVEN
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder
        var transactionExecuted = false
        val body: TransactionWithSpanAndWithReturn<Boolean>.() -> Boolean = {
            true
        }

        // WHEN
        transactionExecuted =
            mockTransacter.transactionTracedWithResult(fakeOperationName, false, body)

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpanBuilder).asChildOf(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
        verify(mockTransacter).transactionWithResult(
            eq(false),
            any<TransactionWithReturn<Boolean>.() -> Boolean>()
        )
        verifyNoMoreInteractions(mockTransacter)
    }

    @Test
    fun `M create Span around W transactionTracedWithResult() without parents`(forge: Forge) {
        // GIVEN
        val fakeNoEnclosing = forge.aBool()
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder
        var transactionExecuted = false
        val body: TransactionWithSpanAndWithReturn<Boolean>.() -> Boolean = {
            true
        }

        // WHEN
        transactionExecuted =
            mockTransacter.transactionTracedWithResult(fakeOperationName, fakeNoEnclosing, body)

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpanBuilder).asChildOf(null as Span?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
        verify(mockTransacter).transactionWithResult(
            eq(fakeNoEnclosing),
            any<TransactionWithReturn<Boolean>.() -> Boolean>()
        )
        verifyNoMoreInteractions(mockTransacter)
    }

    @Test
    fun `M close the Span W transactionTracedWithResult() throws exception`(forge: Forge) {
        // GIVEN
        val fakeNoEnclosing = forge.aBool()
        var caughtException: Throwable? = null
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder

        // WHEN
        try {
            mockTransacter.transactionTracedWithResult<Transacter, Boolean>(
                fakeOperationName,
                noEnclosing = fakeNoEnclosing
            ) {
                throw fakeException
            }
        } catch (e: Throwable) {
            caughtException = fakeException
        }

        // THEN
        assertThat(caughtException).isEqualTo(fakeException)
        verify(mockSpanBuilder).asChildOf(null as Span?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).log(
                argThat<Map<String, Any?>> {
                    this[Fields.ERROR_OBJECT] == fakeException
                }
            )
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
    }

    @Test
    fun `M log the exception W transactionTracedWithResult() throws exception`(forge: Forge) {
        // GIVEN
        val fakeNoEnclosing = forge.aBool()
        var caughtException: Throwable? = null
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder

        // WHEN
        try {
            mockTransacter.transactionTracedWithResult<Transacter, Boolean>(
                fakeOperationName,
                noEnclosing = fakeNoEnclosing
            ) {
                throw fakeException
            }
        } catch (e: Throwable) {
            caughtException = fakeException
        }

        // THEN
        assertThat(caughtException).isEqualTo(fakeException)
        verify(mockSpanBuilder).asChildOf(null as Span?)
        verify(mockSpan).log(
            argThat<Map<String, Any?>> {
                this[Fields.ERROR_OBJECT] == fakeException
            }
        )
    }

    @Test
    fun `M execute the transaction W transactionTracedWithResult()`(forge: Forge) {
        // GIVEN
        val fakeNoEnclosing = forge.aBool()
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder
        var transactionExecuted = false
        val afterCommitLambda = {
        }
        val body: TransactionWithSpanAndWithReturn<Boolean>.() -> Boolean = {
            afterCommit(afterCommitLambda)
            true
        }

        // WHEN
        transactionExecuted =
            mockTransacter.transactionTracedWithResult(fakeOperationName, fakeNoEnclosing, body)

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockTransactionWithReturn).afterCommit(afterCommitLambda)
    }

    @Test
    fun `M add Span tags W transactionTracedWithResult()`(forge: Forge) {
        // GIVEN
        val fakeTagKey = forge.anAlphabeticalString()
        val fakeTagValue = forge.anAlphabeticalString()
        val fakeNoEnclosing = forge.aBool()
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder
        var transactionExecuted = false
        val body: TransactionWithSpanAndWithReturn<Boolean>.() -> Boolean = {
            setTag(fakeTagKey, fakeTagValue)
            true
        }

        // WHEN
        transactionExecuted =
            mockTransacter.transactionTracedWithResult(fakeOperationName, fakeNoEnclosing, body)

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpan).setTag(fakeTagKey, fakeTagValue)
    }
}
