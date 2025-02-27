/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewTreeObserver
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ViewOnDrawInterceptorTest {

    private lateinit var testedInterceptor: ViewOnDrawInterceptor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockOnDrawListenerProducer: OnDrawListenerProducer

    @Mock
    lateinit var mockOnDrawListener: ViewTreeObserver.OnDrawListener

    @Mock
    lateinit var mockTouchPrivacyManager: TouchPrivacyManager

    @Forgery
    lateinit var fakeTextAndInputPrivacy: TextAndInputPrivacy

    @Forgery
    lateinit var fakeImagePrivacy: ImagePrivacy

    private lateinit var fakeDecorViews: List<View>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeDecorViews = forge.aMockedDecorViewsList()

        whenever(
            mockOnDrawListenerProducer.create(
                decorViews = fakeDecorViews,
                textAndInputPrivacy = fakeTextAndInputPrivacy,
                imagePrivacy = fakeImagePrivacy,
                touchPrivacyManager = mockTouchPrivacyManager
            )
        ) doReturn mockOnDrawListener

        testedInterceptor = ViewOnDrawInterceptor(
            internalLogger = mockInternalLogger,
            onDrawListenerProducer = mockOnDrawListenerProducer,
            touchPrivacyManager = mockTouchPrivacyManager
        )
    }

    @Test
    fun `M register the OnDrawListener W intercept()`() {
        // When
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)

        // Then
        fakeDecorViews.forEach {
            argumentCaptor<ViewTreeObserver.OnDrawListener> {
                verify(it.viewTreeObserver).addOnDrawListener(capture())
                assertThat(firstValue).isSameAs(mockOnDrawListener)
            }
        }
    }

    @Test
    fun `M create the OnDrawListener with privacy W intercept()`() {
        // Given
        testedInterceptor = ViewOnDrawInterceptor(
            internalLogger = mockInternalLogger,
            touchPrivacyManager = mockTouchPrivacyManager,
            onDrawListenerProducer = { _, privacy, _, _ ->
                check(privacy == fakeTextAndInputPrivacy) {
                    "Expected to create an OnDrawListener with privacy $fakeTextAndInputPrivacy but was $privacy"
                }
                mock()
            }
        )

        // When
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)

        // Then
        fakeDecorViews.forEach {
            verify(it.viewTreeObserver).addOnDrawListener(any())
        }
    }

    @Test
    fun `M force onDraw on the listener when registered()`() {
        // Given
        val mockOnDrawListener = mock<ViewTreeObserver.OnDrawListener>()
        testedInterceptor = ViewOnDrawInterceptor(
            internalLogger = mockInternalLogger,
            touchPrivacyManager = mockTouchPrivacyManager
        ) { _, _, _, _ -> mockOnDrawListener }

        // When
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)

        // Then
        fakeDecorViews.forEach {
            verify(it.viewTreeObserver).addOnDrawListener(mockOnDrawListener)
        }
        verify(mockOnDrawListener).onDraw()
    }

    @Test
    fun `M register one single listener instance W intercept()`() {
        // When
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)

        // Then
        val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        fakeDecorViews.forEach {
            verify(it.viewTreeObserver).addOnDrawListener(captor.capture())
        }

        captor.allValues.reduce { acc, next ->
            assertThat(acc).isSameAs(next)
            next
        }
    }

    @Test
    fun `M do nothing W intercept() { view tree observer is not alive }`() {
        // Given
        fakeDecorViews.forEach {
            whenever(it.viewTreeObserver.isAlive) doReturn false
        }

        // When
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)

        // Then
        assertThat(testedInterceptor.decorOnDrawListeners).isEmpty()
    }

    @Test
    fun `M do nothing W intercept() { view tree observer throws exception }`() {
        // Given
        fakeDecorViews.forEach {
            whenever(it.viewTreeObserver.addOnDrawListener(any())) doThrow IllegalStateException()
        }

        // When
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)

        // Then
        assertThat(testedInterceptor.decorOnDrawListeners).isEmpty()
    }

    @Test
    fun `M unregister and clean the listeners W stopIntercepting(decorViews)`() {
        // Given
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)

        // When
        testedInterceptor.stopIntercepting(fakeDecorViews)

        // Then
        fakeDecorViews.forEach {
            val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
            verify(it.viewTreeObserver).addOnDrawListener(captor.capture())
            verify(it.viewTreeObserver).removeOnDrawListener(captor.firstValue)
        }
        assertThat(testedInterceptor.decorOnDrawListeners).isEmpty()
    }

    @Test
    fun `M unregister the listeners safely W stopIntercepting(decorViews)`() {
        // Given
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)
        fakeDecorViews.forEach {
            whenever(it.viewTreeObserver.removeOnDrawListener(any())) doThrow IllegalStateException()
        }

        // When
        testedInterceptor.stopIntercepting(fakeDecorViews)

        // Then
        fakeDecorViews.forEach {
            val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
            verify(it.viewTreeObserver).addOnDrawListener(captor.capture())
            verify(it.viewTreeObserver).removeOnDrawListener(captor.firstValue)
        }
        assertThat(testedInterceptor.decorOnDrawListeners).isEmpty()
    }

    @Test
    fun `M unregister and clean the listeners W stopIntercepting()`() {
        // Given
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)

        // When
        testedInterceptor.stopIntercepting()

        // Then
        fakeDecorViews.forEach {
            val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
            verify(it.viewTreeObserver).addOnDrawListener(captor.capture())
            verify(it.viewTreeObserver).removeOnDrawListener(captor.firstValue)
        }
        assertThat(testedInterceptor.decorOnDrawListeners).isEmpty()
    }

    // TODO different privacy ?
    @Test
    fun `M unregister first and clean the listeners W intercepting()`() {
        // Given
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)

        // When
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)

        // Then
        fakeDecorViews.forEach {
            val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
            it.viewTreeObserver.inOrder {
                verify().addOnDrawListener(captor.capture())
                verify().removeOnDrawListener(captor.firstValue)
                verify().addOnDrawListener(captor.capture())
            }
        }
    }

    @Test
    fun `M unregister the listeners safely W stopIntercepting()`() {
        // Given
        testedInterceptor.intercept(fakeDecorViews, fakeTextAndInputPrivacy, fakeImagePrivacy)
        fakeDecorViews.forEach {
            whenever(it.viewTreeObserver.removeOnDrawListener(any())) doThrow IllegalStateException()
        }

        // When
        testedInterceptor.stopIntercepting()

        // Then
        fakeDecorViews.forEach {
            val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
            verify(it.viewTreeObserver).addOnDrawListener(captor.capture())
            verify(it.viewTreeObserver).removeOnDrawListener(captor.firstValue)
        }
        assertThat(testedInterceptor.decorOnDrawListeners).isEmpty()
    }

    // region Internal

    private fun Forge.aMockedDecorViewsList(): List<View> {
        return aList {
            mock {
                val mockViewTreeObserver: ViewTreeObserver = mock()
                whenever(mockViewTreeObserver.isAlive) doReturn true
                whenever(it.viewTreeObserver).thenReturn(mockViewTreeObserver)
            }
        }
    }

    // endregion
}
