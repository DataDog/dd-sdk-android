/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.view.View
import android.view.Window
import com.datadog.android.sessionreplay.processor.Processor
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
internal class RecorderOnDrawListenerTest {

    lateinit var testedListener: RecorderOnDrawListener

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockDecorView: View
    lateinit var mockWindow: Window
    lateinit var mockResources: Resources
    lateinit var configuration: Configuration

    @Mock
    lateinit var mockHandler: Handler

    @Mock
    lateinit var mockSnapshotProducer: SnapshotProducer

    @Mock
    lateinit var mockProcessor: Processor

    @FloatForgery(min = 1.0f, max = 100.0f)
    var fakeDensity: Float = 0f

    @IntForgery(min = 0)
    var fakeDecorWidth: Int = 0

    @IntForgery(min = 0)
    var fakeDecorHeight: Int = 0
    var fakeOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockDecorView.width).thenReturn(fakeDecorWidth)
        whenever(mockDecorView.height).thenReturn(fakeDecorHeight)
        mockWindow = mock {
            whenever(it.decorView).thenReturn(mockDecorView)
        }
        configuration = Configuration()
        fakeOrientation = forge.anElementFrom(
            Configuration
                .ORIENTATION_LANDSCAPE,
            Configuration.ORIENTATION_PORTRAIT
        )
        configuration.orientation = fakeOrientation
        mockResources = mock {
            whenever(it.configuration).thenReturn(configuration)
        }
        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(mockActivity.resources).thenReturn(mockResources)
        testedListener = RecorderOnDrawListener(
            mockActivity,
            fakeDensity,
            mockProcessor,
            mockSnapshotProducer,
            mockHandler
        )
    }

    @Test
    fun `M take and process snapshot W onDraw()`(@Forgery fakeNode: Node) {
        // Given
        stubHandler()
        whenever(mockSnapshotProducer.produce(mockDecorView, fakeDensity)).thenReturn(fakeNode)

        // When
        testedListener.onDraw()

        // Then
        verify(mockProcessor).process(fakeNode)
    }

    @Test
    fun `M do nothing W onDraw() { activity window is null }`(@Forgery fakeNode: Node) {
        // Given
        whenever(mockActivity.window).thenReturn(null)
        stubHandler()
        whenever(mockSnapshotProducer.produce(mockDecorView, fakeDensity)).thenReturn(fakeNode)

        // When
        testedListener.onDraw()

        // Then
        verifyZeroInteractions(mockProcessor)
    }

    @Test
    fun `M send OrientationChanged W onDraw() { first time }`(@Forgery fakeNode: Node) {
        // Given
        stubHandler()
        whenever(mockSnapshotProducer.produce(mockDecorView, fakeDensity)).thenReturn(fakeNode)

        // When
        testedListener.onDraw()

        // Then
        val argumentCaptor = argumentCaptor<OrientationChanged>()
        verify(mockProcessor).process(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue)
            .isEqualTo(
                OrientationChanged(
                    fakeDecorWidth.densityNormalized(fakeDensity),
                    fakeDecorHeight.densityNormalized(fakeDensity)
                )
            )
    }

    @Test
    fun `M send OrientationChanged only once W onDraw() { second time }`(@Forgery fakeNode: Node) {
        // Given
        stubHandler()
        whenever(mockSnapshotProducer.produce(mockDecorView, fakeDensity)).thenReturn(fakeNode)

        // When
        testedListener.onDraw()
        testedListener.onDraw()

        // Then
        val argumentCaptor = argumentCaptor<OrientationChanged>()
        verify(mockProcessor).process(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue)
            .isEqualTo(
                OrientationChanged(
                    fakeDecorWidth.densityNormalized(fakeDensity),
                    fakeDecorHeight.densityNormalized(fakeDensity)
                )
            )
    }

    @Test
    fun `M send OrientationChanged twice W onDraw() { called 2 times with different orientation }`(
        @Forgery fakeNode: Node
    ) {
        // Given
        stubHandler()
        whenever(mockSnapshotProducer.produce(mockDecorView, fakeDensity)).thenReturn(fakeNode)

        // When
        val configuration1 =
            Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        whenever(mockResources.configuration).thenReturn(configuration1)
        testedListener.onDraw()
        val configuration2 =
            Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }
        whenever(mockResources.configuration).thenReturn(configuration2)
        testedListener.onDraw()

        // Then
        val argumentCaptor = argumentCaptor<OrientationChanged>()
        verify(mockProcessor, times(2)).process(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue)
            .isEqualTo(
                OrientationChanged(
                    fakeDecorWidth.densityNormalized(fakeDensity),
                    fakeDecorHeight.densityNormalized(fakeDensity)
                )
            )
        assertThat(argumentCaptor.secondValue)
            .isEqualTo(
                OrientationChanged(
                    fakeDecorWidth.densityNormalized(fakeDensity),
                    fakeDecorHeight.densityNormalized(fakeDensity)
                )
            )
    }

    private fun stubHandler() {
        whenever(
            mockHandler.postDelayed(
                any(),
                eq(RecorderOnDrawListener.DEBOUNCE_DURATION_IN_MILLIS)
            )
        )
            .then {
                (it.arguments[0] as Runnable).run()
                true
            }
    }
}
