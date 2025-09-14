/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.internal.utils

import android.view.Window
import com.datadog.android.internal.utils.onContentChanged
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@MockitoSettings(strictness = Strictness.LENIENT)
class WindowCallbackUtilsTest {

    @Mock
    private lateinit var window: Window

    @Mock
    private lateinit var existingCallback: Window.Callback

    private var callback: Window.Callback? = null

    @BeforeEach
    fun `set up`() {
        callback = existingCallback

        whenever(window.callback) doReturn callback
        whenever(window.setCallback(any())).doAnswer {
            val arg = it.getArgument<Window.Callback>(0)
            callback = arg
        }
    }

    @Test
    fun `M notify both callbacks W onContentChanged`() {
        val onContentChanged = mock<() -> Boolean>()

        whenever(onContentChanged.invoke()) doReturn true

        window.onContentChanged(onContentChanged)

        callback!!.onContentChanged()

        inOrder(existingCallback, onContentChanged) {
            verify(onContentChanged).invoke()
            verify(existingCallback).onContentChanged()
        }
    }

    @Test
    fun `M remove the callback W onContentChanged { if it returns false }`() {
        // Given
        val onContentChanged = mock<() -> Boolean>()
        whenever(onContentChanged.invoke()) doReturn false

        window.onContentChanged(onContentChanged)

        // When
        callback!!.onContentChanged()
        callback!!.onContentChanged()

        // Then
        inOrder(existingCallback, onContentChanged) {
            verify(onContentChanged).invoke()
            verify(existingCallback, times(2)).onContentChanged()
        }
        verifyNoMoreInteractions(onContentChanged, existingCallback)
    }
}
