/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@MockitoSettings(strictness = Strictness.LENIENT)
class DDCoreSubscriptionTest {

    @Mock
    private lateinit var listener: TestListener

    private val subscription = DDCoreSubscription.create<TestListener>()

    @Test
    fun `M notify listeners W notifyListeners {multiple listeners}`() {
        // Given
        val listener2 = mock<TestListener>()

        subscription.addListener(listener)
        subscription.addListener(listener2)

        // When
        subscription.notifyListeners { onSomethingChanged() }

        // Then
        assertThat(subscription.listenersCount).isEqualTo(2)
        inOrder(listener, listener2) {
            verify(listener).onSomethingChanged()
            verify(listener2).onSomethingChanged()
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M remove listener W removeListener`() {
        // Given
        subscription.addListener(listener)

        // When
        subscription.removeListener(listener)
        subscription.notifyListeners { onSomethingChanged() }

        // Then
        assertThat(subscription.listenersCount).isZero
        verifyNoInteractions(listener)
    }

    @Test
    fun `M call all listeners W notifyListeners { if listener is removed during notifyListeners }`() {
        // Given
        val listener2 = mock<TestListener>()
        whenever(listener2.onSomethingChanged()).doAnswer {
            subscription.removeListener(listener)
        }

        subscription.addListener(listener2)
        subscription.addListener(listener)

        // When
        subscription.notifyListeners { onSomethingChanged() }

        // Then
        assertThat(subscription.listenersCount).isEqualTo(1)
        inOrder(listener, listener2) {
            verify(listener2).onSomethingChanged()
            verify(listener).onSomethingChanged()
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M not call a new listener W notifyListeners { if it is added during notifyListeners }`() {
        // Given
        val listener2 = mock<TestListener>()
        whenever(listener2.onSomethingChanged()).doAnswer {
            subscription.addListener(listener)
        }

        subscription.addListener(listener2)

        // When
        subscription.notifyListeners { onSomethingChanged() }

        // Then
        assertThat(subscription.listenersCount).isEqualTo(2)

        verify(listener2).onSomethingChanged()
        verifyNoMoreInteractions(listener, listener2)
    }
}

private interface TestListener {
    fun onSomethingChanged()
}
