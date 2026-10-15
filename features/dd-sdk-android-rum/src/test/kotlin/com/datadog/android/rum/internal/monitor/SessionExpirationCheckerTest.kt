/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import android.os.Handler
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SessionExpirationCheckerTest {
    private lateinit var testedChecker: SessionExpirationChecker

    @Mock lateinit var mockMonitor: DatadogRumMonitor

    @Mock lateinit var mockHandler: Handler

    @Mock lateinit var stubTimeProvider: TimeProvider

    @LongForgery(min = 1L, max = 600_000L)
    var fakeIntervalMs: Long = 0L

    @BeforeEach
    fun setUp() {
        testedChecker = SessionExpirationChecker(mockMonitor, stubTimeProvider, fakeIntervalMs, mockHandler)
    }

    @Test
    fun `M replace pending check W onEventHandled() {tracked session}`() {
        // When
        testedChecker.onEventHandled(RumSessionScope.State.TRACKED)

        // Then
        val fakeCallback = scheduledCallback()
        inOrder(mockHandler) {
            verify(mockHandler).removeCallbacks(same(fakeCallback))
            verify(mockHandler).postDelayed(same(fakeCallback), eq(fakeIntervalMs))
        }
    }

    @ParameterizedTest
    @EnumSource(RumSessionScope.State::class, names = ["TRACKED"], mode = EnumSource.Mode.EXCLUDE)
    fun `M only cancel pending check W onEventHandled() {session not tracked}`(
        fakeState: RumSessionScope.State
    ) {
        // When
        testedChecker.onEventHandled(fakeState)

        // Then
        verify(mockHandler).removeCallbacks(any())
        verify(mockHandler, never()).postDelayed(any(), any())
    }

    @Test
    fun `M only cancel pending check W onEventHandled() {no session}`() {
        // When
        testedChecker.onEventHandled(null)

        // Then
        verify(mockHandler).removeCallbacks(any())
        verify(mockHandler, never()).postDelayed(any(), any())
    }

    @Test
    fun `M schedule check again W onEventHandled() {session renewed after expiry}`() {
        // Given
        testedChecker.onEventHandled(RumSessionScope.State.TRACKED)
        val fakeCallback = scheduledCallback()
        testedChecker.onEventHandled(RumSessionScope.State.EXPIRED)
        clearInvocations(mockHandler)

        // When
        testedChecker.onEventHandled(RumSessionScope.State.TRACKED)

        // Then
        verify(mockHandler).postDelayed(same(fakeCallback), eq(fakeIntervalMs))
    }

    @Test
    fun `M send expiry event without rescheduling W run()`() {
        // Given
        testedChecker.onEventHandled(RumSessionScope.State.TRACKED)
        val fakeCallback = scheduledCallback()
        clearInvocations(mockHandler)

        // When
        fakeCallback.run()

        // Then
        verify(mockMonitor).handleEvent(any<RumRawEvent.SessionExpiryCheck>())
        verifyNoInteractions(mockHandler)
    }

    @Test
    fun `M cancel pending check W onSdkStopped()`() {
        // Given
        testedChecker.onEventHandled(RumSessionScope.State.TRACKED)
        val fakeCallback = scheduledCallback()
        clearInvocations(mockHandler)

        // When
        testedChecker.onSdkStopped()

        // Then
        verify(mockHandler).removeCallbacks(same(fakeCallback))
    }

    @Test
    fun `M not schedule check W onSdkStopped() + onEventHandled() {tracked session}`() {
        // When
        testedChecker.onSdkStopped()
        testedChecker.onEventHandled(RumSessionScope.State.TRACKED)

        // Then
        verify(mockHandler, never()).postDelayed(any(), any())
    }

    @Test
    fun `M not send expiry event W onSdkStopped() + run()`() {
        // Given
        testedChecker.onEventHandled(RumSessionScope.State.TRACKED)
        val fakeCallback = scheduledCallback()

        // When
        testedChecker.onSdkStopped()
        fakeCallback.run()

        // Then
        verifyNoInteractions(mockMonitor)
    }

    @Test
    fun `M send expiry event without touching pending check W checkNow()`() {
        // When
        testedChecker.checkNow()

        // Then
        verify(mockMonitor).handleEvent(any<RumRawEvent.SessionExpiryCheck>())
        verifyNoInteractions(mockHandler)
    }

    @Test
    fun `M not send expiry event W onSdkStopped() + checkNow()`() {
        // When
        testedChecker.onSdkStopped()
        testedChecker.checkNow()

        // Then
        verifyNoInteractions(mockMonitor)
    }

    @Test
    fun `M apply updated interval W onEventHandled()`(
        @LongForgery(1_000_000L, 5_000_000L) fakeInterval: Long
    ) {
        // Given
        testedChecker.checkIntervalMs = fakeInterval

        // When
        testedChecker.onEventHandled(RumSessionScope.State.TRACKED)

        // Then
        assertThat(testedChecker.checkIntervalMs).isEqualTo(fakeInterval)
        verify(mockHandler).postDelayed(any(), eq(fakeInterval))
    }

    private fun scheduledCallback(): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockHandler).postDelayed(captor.capture(), eq(fakeIntervalMs))
        return captor.firstValue
    }
}
