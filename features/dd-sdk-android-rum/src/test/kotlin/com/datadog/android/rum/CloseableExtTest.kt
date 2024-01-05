/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.api.SdkCore
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.Closeable
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloseableExtTest {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockCloseable: Closeable

    @Forgery
    lateinit var fakeException: Throwable

    @StringForgery
    lateinit var fakeString: String

    @BeforeEach
    fun `set up`() {
        GlobalRumMonitor::class.declaredFunctions.first { it.name == "registerIfAbsent" }.apply {
            isAccessible = true
            call(GlobalRumMonitor::class.objectInstance, mockRumMonitor, mockSdkCore)
        }
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
    }

    @Test
    fun `M send an error event W exception in the block`() {
        // GIVEN
        val caughtException: Throwable?

        // WHEN
        try {
            mockCloseable.useMonitored(mockSdkCore) {
                throw fakeException
            }
        } catch (e: Throwable) {
            caughtException = e
        }

        // THEN
        assertThat(caughtException).isEqualTo(fakeException)
        verify(mockRumMonitor).addError(
            CLOSABLE_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            fakeException,
            emptyMap()
        )
    }

    @Test
    fun `M close the closeable instance W exception in the block`() {
        // GIVEN
        val caughtException: Throwable?

        // WHEN
        try {
            mockCloseable.useMonitored(mockSdkCore) {
                throw fakeException
            }
        } catch (e: Throwable) {
            caughtException = e
        }

        // THEN
        assertThat(fakeException).isEqualTo(caughtException)
        verify(mockCloseable).close()
        verifyNoMoreInteractions(mockCloseable)
    }

    @Test
    fun `M send an error event W exception on close`() {
        // GIVEN
        var caughtException: Throwable? = null
        whenever(mockCloseable.close()) doThrow fakeException

        // WHEN
        try {
            mockCloseable.useMonitored(mockSdkCore) {}
        } catch (e: Throwable) {
            caughtException = e
        }

        // THEN
        assertThat(caughtException).isNull()
        verify(mockRumMonitor).addError(
            CLOSABLE_ERROR_MESSAGE,
            RumErrorSource.SOURCE,
            fakeException,
            emptyMap()
        )
    }

    @Test
    fun `M close the closeable instance W no exception in the block`() {
        // WHEN
        val returnedValue = mockCloseable.useMonitored(mockSdkCore) {
            fakeString
        }

        // THEN
        assertThat(returnedValue).isEqualTo(fakeString)
        verify(mockCloseable).close()
        verifyNoMoreInteractions(mockCloseable)
    }
}
