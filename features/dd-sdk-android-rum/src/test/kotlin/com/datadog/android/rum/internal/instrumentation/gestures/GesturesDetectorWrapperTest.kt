/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")
// TODO RUM-11898 Migrate deprecated GestureDetectorCompat

package com.datadog.android.rum.internal.instrumentation.gestures

import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class GesturesDetectorWrapperTest {

    lateinit var testedWrapper: GesturesDetectorWrapper

    @Mock
    lateinit var mockGesturesDetectorListener: GesturesListener

    @Mock
    lateinit var mockGesturesDetectorCompat: GestureDetectorCompat

    @BeforeEach
    fun `set up`() {
        testedWrapper = GesturesDetectorWrapper(
            mockGesturesDetectorListener,
            mockGesturesDetectorCompat
        )
    }

    @Test
    fun `it will delegate the events to the bundled compat detector`() {
        val event: MotionEvent = mock()
        testedWrapper.onTouchEvent(event)
        verify(mockGesturesDetectorCompat).onTouchEvent(event)
    }

    @Test
    fun `on action up will call the gesture listener after delegating to gestures detector`() {
        val event: MotionEvent = mock {
            whenever(it.actionMasked).thenReturn(MotionEvent.ACTION_UP)
        }
        testedWrapper.onTouchEvent(event)
        inOrder(mockGesturesDetectorCompat, mockGesturesDetectorListener) {
            verify(mockGesturesDetectorCompat).onTouchEvent(event)
            verify(mockGesturesDetectorListener).onUp(event)
        }
    }

    @Test
    fun `in different than action up will not interact with the listener`(forge: Forge) {
        val event: MotionEvent = mock {
            whenever(it.actionMasked).thenReturn(
                forge.anElementFrom(
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_SCROLL,
                    MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_MOVE
                )
            )
        }

        testedWrapper.onTouchEvent(event)

        verifyNoInteractions(mockGesturesDetectorListener)
    }

    @Test
    fun `M not call 'onUp' W action up is consumed`() {
        // Given
        val event: MotionEvent = mock {
            whenever(it.actionMasked).thenReturn(MotionEvent.ACTION_UP)
        }
        whenever(mockGesturesDetectorCompat.onTouchEvent(event)).thenReturn(true)

        // When
        testedWrapper.onTouchEvent(event)

        // Given
        verify(mockGesturesDetectorCompat).onTouchEvent(event)
        verify(mockGesturesDetectorListener, never()).onUp(any())
    }
}
