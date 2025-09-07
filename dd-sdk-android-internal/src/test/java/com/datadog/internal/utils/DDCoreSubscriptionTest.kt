/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.internal.utils

import com.datadog.android.internal.utils.DDCoreSubscription
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness

@MockitoSettings(strictness = Strictness.LENIENT)
class DDCoreSubscriptionTest {

    @Test
    fun `M notify listeners W DDCoreSubscription notifyListeners {multiple listeners}`() {
        val listener1 = mock<TestListener>()
        val listener2 = mock<TestListener>()

        val subscription = DDCoreSubscription.create<TestListener>()
        subscription.addListener(listener1)
        subscription.addListener(listener2)

        subscription.notifyListeners { onSomethingChanged() }

        verify(listener1).onSomethingChanged()
        verifyNoMoreInteractions(listener1)

        verify(listener2).onSomethingChanged()
        verifyNoMoreInteractions(listener2)
    }

    @Test
    fun `M remove listener W DDCoreSubscription removeListener`() {
        val listener1 = mock<TestListener>()

        val subscription = DDCoreSubscription.create<TestListener>()
        subscription.addListener(listener1)

        subscription.notifyListeners { onSomethingChanged() }

        verify(listener1).onSomethingChanged()
        verifyNoMoreInteractions(listener1)

        subscription.removeListener(listener1)

        subscription.notifyListeners { onSomethingChanged() }

        verifyNoMoreInteractions(listener1)
    }
}

private interface TestListener {
    fun onSomethingChanged()
}
