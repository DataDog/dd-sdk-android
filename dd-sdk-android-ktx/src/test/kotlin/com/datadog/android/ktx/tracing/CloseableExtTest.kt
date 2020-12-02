/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.tracing

import com.datadog.android.ktx.rum.CLOSABLE_ERROR_NESSAGE
import com.datadog.android.ktx.rum.useMonitored
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.getStaticValue
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
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

    @Forgery
    lateinit var fakeException: Throwable

    @Mock
    lateinit var testMockCloseable: Closeable

    @StringForgery
    lateinit var fakeString: String

    @BeforeEach
    fun `set up`() {
        GlobalRum.registerIfAbsent(mockRumMonitor)
    }

    @AfterEach
    fun `tear down`() {
        val isRegistered: AtomicBoolean = GlobalRum::class.java.getStaticValue("isRegistered")
        isRegistered.set(false)
    }

    @Test
    fun `M send an error event W exception in the block`(forge: Forge) {
        // GIVEN
        var caughtException: Throwable? = null

        // WHEN
        try {
            testMockCloseable.useMonitored {
                throw fakeException
            }
        } catch (e: Throwable) {
            caughtException = e
        }

        // THEN
        assertThat(caughtException).isEqualTo(fakeException)
        verify(mockRumMonitor).addError(
            CLOSABLE_ERROR_NESSAGE,
            RumErrorSource.SOURCE,
            fakeException,
            emptyMap()
        )
    }

    @Test
    fun `M close the closeable instance W exception in the block`(forge: Forge) {
        // GIVEN
        var caughtException: Throwable? = null
        // WHEN
        try {
            testMockCloseable.useMonitored {
                throw fakeException
            }
        } catch (e: Throwable) {
            caughtException = e
        }

        // THEN
        assertThat(fakeException).isEqualTo(caughtException)
        verify(testMockCloseable).close()
        verifyNoMoreInteractions(testMockCloseable)
    }

    @Test
    fun `M close the closeable instance W no exception in the block`(forge: Forge) {
        // WHEN
        val returnedValue = testMockCloseable.useMonitored {
            fakeString
        }

        // THEN
        assertThat(returnedValue).isEqualTo(fakeString)
        verify(testMockCloseable).close()
        verifyNoMoreInteractions(testMockCloseable)
    }
}
