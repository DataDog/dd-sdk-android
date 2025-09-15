/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils

import android.app.Activity
import android.view.Window
import com.datadog.android.rum.internal.instrumentation.gestures.FixedWindowCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class RumWindowCallbacksRegistryTest {

    @Mock
    private lateinit var activity: Activity

    @Mock
    private lateinit var window: Window

    @Mock
    private lateinit var existingCallback: Window.Callback

    @Mock
    private lateinit var listener: RumWindowCallbackListener

    private var callback: Window.Callback? = null

    private val registry = RumWindowCallbacksRegistryImpl()

    @BeforeEach
    fun setUp() {
        callback = existingCallback

        whenever(activity.window) doReturn window
        whenever(window.callback).doAnswer {
            callback
        }
        whenever(window.setCallback(any())).doAnswer {
            val argCallback = it.getArgument<Window.Callback>(0)
            callback = argCallback
        }
    }

    @Test
    fun `M call existing callback and listener W RumWindowCallbacksRegistry { onContentChanged called }`() {
        // Given
        registry.addListener(activity, listener)
        val callbackAfterListener1 = window.callback

        // When
        callback!!.onContentChanged()

        // Then
        inOrder(listener, existingCallback) {
            verify(existingCallback).onContentChanged()
            verify(listener).onContentChanged()
        }
        verifyNoMoreInteractions(listener, existingCallback)
        assertThat(callback).isEqualTo(callbackAfterListener1)
    }

    @Test
    fun `M restore existing callback W RumWindowCallbacksRegistry { after removeListener called }`() {
        // Given
        registry.addListener(activity, listener)

        // When
        registry.removeListener(activity, listener)

        // Then
        assertThat(callback).isEqualTo(existingCallback)
    }

    @Test
    fun `M call both listeners and use the same WindowCallback W RumWindowCallbacksRegistry`() {
        // Given
        registry.addListener(activity, listener)
        val callbackAfterListener1 = window.callback

        val listener2 = mock<RumWindowCallbackListener>()
        registry.addListener(activity, listener2)

        // When
        callback!!.onContentChanged()

        // Then
        inOrder(listener, listener2, existingCallback) {
            verify(existingCallback).onContentChanged()
            verify(listener).onContentChanged()
            verify(listener2).onContentChanged()
        }
        verifyNoMoreInteractions(listener, listener2, existingCallback)
        assertThat(callback).isEqualTo(callbackAfterListener1)
    }

    @Test
    fun `M not restore the callback W RumWindowCallbacksRegistry { if there is another wrapper }`() {
        // Given
        registry.addListener(activity, listener)

        val anotherWrapper = TestWindowCallbackWrapper(window.callback)
        window.callback = anotherWrapper

        // When
        registry.removeListener(activity, listener)

        // Then
        assertThat(callback).isEqualTo(anotherWrapper)
    }
}

private class TestWindowCallbackWrapper(wrapped: Window.Callback): FixedWindowCallback(wrapped)
