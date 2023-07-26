/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.sqlite

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SqliteDatabaseExtTest {

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
    lateinit var mockDatabase: SQLiteDatabase

    @Forgery
    lateinit var fakeException: Throwable

    @BeforeEach
    fun `set up`() {
        GlobalTracer.registerIfAbsent(mockTracer)
        whenever(mockTracer.buildSpan(fakeOperationName)) doReturn mockSpanBuilder
        whenever(mockTracer.activateSpan(mockSpan)) doReturn mockScope
        whenever(mockSpanBuilder.start()) doReturn mockSpan
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @Test
    fun `M create Span around transaction W transactionTraced() {exclusive = true}`() {
        // GIVEN
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder

        // WHEN
        val transactionExecuted: Boolean = mockDatabase.transactionTraced(
            fakeOperationName,
            true
        ) {
            true
        }

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpanBuilder).asChildOf(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
        inOrder(mockDatabase) {
            verify(mockDatabase).beginTransaction()
            verify(mockDatabase).setTransactionSuccessful()
            verify(mockDatabase).endTransaction()
        }
    }

    @Test
    fun `M create Span around transaction W transactionTraced() {exclusive = false}`() {
        // GIVEN
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder

        // WHEN
        val transactionExecuted: Boolean = mockDatabase.transactionTraced(
            fakeOperationName,
            false
        ) {
            true
        }

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpanBuilder).asChildOf(mockParentSpan)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
        inOrder(mockDatabase) {
            verify(mockDatabase).beginTransactionNonExclusive()
            verify(mockDatabase).setTransactionSuccessful()
            verify(mockDatabase).endTransaction()
        }
    }

    @Test
    fun `M create Span around transaction W transactionTraced() without parents`() {
        // GIVEN
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder

        // WHEN
        val transactionExecuted = mockDatabase.transactionTraced(
            fakeOperationName,
            false
        ) {
            true
        }

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpanBuilder).asChildOf(null as Span?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
        inOrder(mockDatabase) {
            verify(mockDatabase).beginTransactionNonExclusive()
            verify(mockDatabase).setTransactionSuccessful()
            verify(mockDatabase).endTransaction()
        }
    }

    @Test
    fun `M close the Span around transaction W transactionTraced() throws exception`() {
        // GIVEN
        whenever(mockTracer.activeSpan()) doReturn null
        whenever(mockSpanBuilder.asChildOf(null as Span?)) doReturn mockSpanBuilder

        // WHEN

        assertThat(
            catchThrowable {
                mockDatabase.transactionTraced(fakeOperationName) {
                    throw fakeException
                }
            }
        ).isEqualTo(fakeException)

        // THEN
        verify(mockSpanBuilder).asChildOf(null as Span?)
        inOrder(mockSpan, mockScope) {
            verify(mockSpan).finish()
            verify(mockScope).close()
        }
        inOrder(mockDatabase) {
            verify(mockDatabase).beginTransaction()
            verify(mockDatabase).endTransaction()
        }
    }

    @Test
    fun `M decorate the Span from lambda W transactionTraced`(forge: Forge) {
        // GIVEN
        val fakeTagKey = forge.anAlphabeticalString()
        val fakeTagValue = forge.anAlphabeticalString()
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder

        // WHEN
        val transactionExecuted = mockDatabase.transactionTraced(
            fakeOperationName,
            false
        ) {
            setTag(fakeTagKey, fakeTagValue)
            true
        }

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockSpan).setTag(fakeTagKey, fakeTagValue)
    }

    @Test
    fun `M execute the lambda on the SQLiteDatabase instance W transactionTraced`(forge: Forge) {
        // GIVEN
        val fakeTable = forge.anAlphabeticalString()
        val contentValues = ContentValues()
        whenever(mockTracer.activeSpan()) doReturn mockParentSpan
        whenever(mockSpanBuilder.asChildOf(mockParentSpan)) doReturn mockSpanBuilder

        // WHEN
        val transactionExecuted = mockDatabase.transactionTraced(
            fakeOperationName,
            false
        ) { mockDatabase ->
            mockDatabase.insert(fakeTable, null, contentValues)
            true
        }

        // THEN
        assertThat(transactionExecuted).isTrue()
        verify(mockDatabase).insert(fakeTable, null, contentValues)
    }
}
