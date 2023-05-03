/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback

import android.app.Activity
import android.app.Dialog
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.datadog.android.sessionreplay.internal.recorder.WindowCallbackInterceptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class RecorderFragmentLifecycleCallbackTest {

    lateinit var testedCallback: RecorderFragmentLifecycleCallback

    @Mock
    lateinit var mockWindowCallbackInterceptor: WindowCallbackInterceptor

    @Mock
    lateinit var mockDialogFragment: DialogFragment

    @Mock
    lateinit var mockActivityWindow: Window

    @Mock
    lateinit var mockDialogWindow: Window

    @Mock
    lateinit var mockOwnerActivity: Activity

    @Mock
    lateinit var mockDialog: Dialog

    @BeforeEach
    fun `set up`() {
        whenever(mockDialogFragment.context).thenReturn(mock())
        whenever(mockDialogFragment.dialog).thenReturn(mockDialog)
        whenever(mockDialog.ownerActivity).thenReturn(mockOwnerActivity)
        whenever(mockOwnerActivity.window).thenReturn(mockActivityWindow)
        whenever(mockDialog.window).thenReturn(mockDialogWindow)
        testedCallback = RecorderFragmentLifecycleCallback(mockWindowCallbackInterceptor)
    }

    // region Different Window from Activity

    @Test
    fun `M start intercepting the dialog window W onFragmentResumed{different windows}`() {
        // When
        testedCallback.onFragmentResumed(mock(), mockDialogFragment)

        // Then
        val captor = argumentCaptor<List<Window>>()
        verify(mockWindowCallbackInterceptor).intercept(captor.capture(), eq(mockOwnerActivity))
        assertThat(captor.firstValue).containsExactlyElementsOf(listOf(mockDialogWindow))
    }

    @Test
    fun `M stop intercepting the dialog window W onFragmentPaused{different windows}`() {
        // When
        testedCallback.onFragmentPaused(mock(), mockDialogFragment)

        // Then
        val captor = argumentCaptor<List<Window>>()
        verify(mockWindowCallbackInterceptor).stopIntercepting(captor.capture())
        assertThat(captor.firstValue).containsExactlyElementsOf(listOf(mockDialogWindow))
    }

    // endregion

    // region Same Window with Activity

    @Test
    fun `M do nothing W onFragmentResumed{same windows}`() {
        // When
        whenever(mockDialog.window).thenReturn(mockActivityWindow)
        testedCallback.onFragmentResumed(mock(), mockDialogFragment)

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    @Test
    fun `M do nothing W onFragmentPaused{same windows}`() {
        // When
        whenever(mockDialog.window).thenReturn(mockActivityWindow)
        testedCallback.onFragmentPaused(mock(), mockDialogFragment)

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    // endregion

    // region Misc

    @Test
    fun `M do nothing W onFragmentResumed{no dialog fragment}`() {
        // When
        testedCallback.onFragmentResumed(mock(), mock())

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    @Test
    fun `M do nothing W onFragmentResumed{no context for dialog fragment}`() {
        // Given
        whenever(mockDialogFragment.context).thenReturn(null)

        // When
        testedCallback.onFragmentResumed(mock(), mockDialogFragment)

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    @Test
    fun `M do nothing W onFragmentResumed{no owner activity for dialog fragment}`() {
        // Given
        whenever(mockDialog.ownerActivity).thenReturn(null)

        // When
        testedCallback.onFragmentResumed(mock(), mockDialogFragment)

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    @Test
    fun `M do nothing W onFragmentResumed{no dialog for dialog fragment}`() {
        // Given
        whenever(mockDialogFragment.dialog).thenReturn(null)

        // When
        testedCallback.onFragmentResumed(mock(), mockDialogFragment)

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    @Test
    fun `M do nothing W onFragmentResumed{no window for ownerActivity}`() {
        // Given
        whenever(mockOwnerActivity.window).thenReturn(null)

        // When
        testedCallback.onFragmentResumed(mock(), mockDialogFragment)

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    @Test
    fun `M do nothing W onFragmentPaused{no dialog fragment}`() {
        // When
        testedCallback.onFragmentPaused(mock(), mock())

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    @Test
    fun `M do nothing W onFragmentPaused{no context for dialog fragment}`() {
        // Given
        whenever(mockDialogFragment.context).thenReturn(null)

        // When
        testedCallback.onFragmentPaused(mock(), mockDialogFragment)

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    @Test
    fun `M do nothing W onFragmentPaused{no owner activity for dialog fragment}`() {
        // Given
        whenever(mockDialog.ownerActivity).thenReturn(null)

        // When
        testedCallback.onFragmentPaused(mock(), mockDialogFragment)

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    @Test
    fun `M do nothing W onFragmentPaused{no dialog for dialog fragment}`() {
        // Given
        whenever(mockDialogFragment.dialog).thenReturn(null)

        // When
        testedCallback.onFragmentPaused(mock(), mockDialogFragment)

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    @Test
    fun `M do nothing W onFragmentPaused{no window for ownerActivity}`() {
        // Given
        whenever(mockOwnerActivity.window).thenReturn(null)

        // When
        testedCallback.onFragmentPaused(mock(), mockDialogFragment)

        // Then
        verifyNoInteractions(mockWindowCallbackInterceptor)
    }

    // endregion
}
