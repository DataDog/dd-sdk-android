/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.app.Activity
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewTreeObserver
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ViewOnDrawInterceptorTest {

    lateinit var testedInterceptor: ViewOnDrawInterceptor

    @Mock
    lateinit var mockRecordedDataQueueHandler: RecordedDataQueueHandler

    @Mock
    lateinit var mockSnapshotProducer: SnapshotProducer

    lateinit var fakeDecorViews: List<View>

    lateinit var mockActivity: Activity

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockActivity = forge.aMockedActivity()
        fakeDecorViews = forge.aMockedDecorViewsList()
        testedInterceptor = ViewOnDrawInterceptor(
            mockRecordedDataQueueHandler,
            mockSnapshotProducer
        )
    }

    @Test
    fun `M register the OnDrawListener W intercept()`() {
        // When
        testedInterceptor.intercept(fakeDecorViews, mockActivity)

        // Then
        val captor = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        fakeDecorViews.forEach {
            verify(it.viewTreeObserver).addOnDrawListener(captor.capture())
        }
        captor.allValues.forEach { assertThat(it).isInstanceOf(WindowsOnDrawListener::class.java) }
    }

    @Test
    fun `M force onDraw on the listener when registered()`() {
        // Given
        val mockOnDrawListener = mock<ViewTreeObserver.OnDrawListener>()
        testedInterceptor = ViewOnDrawInterceptor(
            mockRecordedDataQueueHandler,
            mockSnapshotProducer
        ) { _, _ -> mockOnDrawListener }

        // When
        testedInterceptor.intercept(fakeDecorViews, mockActivity)

        // Then
        fakeDecorViews.forEach {
            verify(it.viewTreeObserver).addOnDrawListener(mockOnDrawListener)
        }
        verify(mockOnDrawListener).onDraw()
    }

    @Test
    fun `M register one single listener instance W intercept()`() {
        // When
        testedInterceptor.intercept(fakeDecorViews, mockActivity)

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
    fun `M unregister and clean the listeners W stopIntercepting(decorViews)`() {
        // Given
        testedInterceptor.intercept(fakeDecorViews, mockActivity)

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
        testedInterceptor.intercept(fakeDecorViews, mockActivity)

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

    @Test
    fun `M unregister first and clean the listeners W intercepting()`() {
        // Given
        testedInterceptor.intercept(fakeDecorViews, mockActivity)

        // When
        testedInterceptor.intercept(fakeDecorViews, mockActivity)

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
    fun `M register listener startRecording() { more activities }`(forge: Forge) {
        // Given
        val fakeDecorsActivitiesPairs = forge.aList {
            aMockedDecorViewsList() to aMockedActivity()
        }

        // When
        fakeDecorsActivitiesPairs.forEach {
            testedInterceptor.intercept(it.first, it.second)
        }

        // Then
        fakeDecorsActivitiesPairs.map { it.first }.flatten().forEach {
            it.viewTreeObserver.inOrder {
                verify().addOnDrawListener(any())
            }
        }
    }

    // region Internal

    private fun Forge.aMockedActivity(): Activity {
        val mockActivity: Activity = mock()
        val fakeDensity = aPositiveFloat()
        val displayMetrics = DisplayMetrics().apply { density = fakeDensity }
        val mockResources: Resources = mock {
            whenever(it.displayMetrics).thenReturn(displayMetrics)
        }
        whenever(mockActivity.resources).thenReturn(mockResources)
        return mockActivity
    }

    private fun Forge.aMockedDecorViewsList(): List<View> {
        return aList {
            mock {
                whenever(it.viewTreeObserver).thenReturn(mock())
            }
        }
    }

    // endregion
}
