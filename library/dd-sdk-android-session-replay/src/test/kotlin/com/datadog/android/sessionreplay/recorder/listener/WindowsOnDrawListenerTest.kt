/*
* Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
* This product includes software developed at Datadog (https://www.datadoghq.com/).
* Copyright 2016-Present Datadog, Inc.
*/

package com.datadog.android.sessionreplay.recorder.listener

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.view.View
import android.view.Window
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.processor.Processor
import com.datadog.android.sessionreplay.recorder.Debouncer
import com.datadog.android.sessionreplay.recorder.Node
import com.datadog.android.sessionreplay.recorder.OrientationChanged
import com.datadog.android.sessionreplay.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.recorder.densityNormalized
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WindowsOnDrawListenerTest {

    lateinit var testedListener: WindowsOnDrawListener

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockDecorView: View
    lateinit var mockWindow: Window
    lateinit var mockResources: Resources
    lateinit var configuration: Configuration

    @Mock
    lateinit var mockSnapshotProducer: SnapshotProducer

    @Mock
    lateinit var mockProcessor: Processor

    @Mock
    lateinit var mockDebouncer: Debouncer

    @FloatForgery(min = 1.0f, max = 100.0f)
    var fakeDensity: Float = 0f

    @IntForgery(min = 0)
    var fakeDecorWidth: Int = 0

    @IntForgery(min = 0)
    var fakeDecorHeight: Int = 0
    var fakeOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    lateinit var fakeMockedWindows: List<Window>
    lateinit var fakeWindowsSnapshots: List<Node>

    @Mock
    lateinit var mockTheme: Theme

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeMockedWindows = forge.aMockedWindowsList()
        fakeWindowsSnapshots = fakeMockedWindows.map { forge.getForgery() }
        whenever(mockActivity.theme).thenReturn(mockTheme)
        fakeMockedWindows.forEachIndexed { index, window ->
            whenever(mockSnapshotProducer.produce(mockTheme, window.decorView, fakeDensity))
                .thenReturn(fakeWindowsSnapshots[index])
        }
        whenever(mockDecorView.width).thenReturn(fakeDecorWidth)
        whenever(mockDecorView.height).thenReturn(fakeDecorHeight)
        configuration = Configuration()
        fakeOrientation = forge.anElementFrom(
            Configuration
                .ORIENTATION_LANDSCAPE,
            Configuration.ORIENTATION_PORTRAIT
        )
        mockWindow = mock {
            whenever(it.decorView).thenReturn(mockDecorView)
        }
        whenever(mockActivity.window).thenReturn(mockWindow)
        configuration.orientation = fakeOrientation
        mockResources = mock {
            whenever(it.configuration).thenReturn(configuration)
        }
        whenever(mockActivity.resources).thenReturn(mockResources)
        testedListener = WindowsOnDrawListener(
            mockActivity,
            fakeMockedWindows,
            fakeDensity,
            mockProcessor,
            mockSnapshotProducer,
            mockDebouncer
        )
    }

    @Test
    fun `M take and process snapshot W onDraw()`() {
        // Given
        stubDebouncer()

        // When
        testedListener.onDraw()

        // Then
        val argumentCaptor = argumentCaptor<OrientationChanged>()
        verify(mockProcessor).processScreenSnapshots(
            eq(fakeWindowsSnapshots),
            argumentCaptor.capture()
        )
        assertThat(argumentCaptor.firstValue)
            .isEqualTo(
                OrientationChanged(
                    fakeDecorWidth.densityNormalized(fakeDensity),
                    fakeDecorHeight.densityNormalized(fakeDensity)
                )
            )
    }

    @Test
    fun `M do nothing W onDraw() { activity window is null }`() {
        // Given
        whenever(mockActivity.window).thenReturn(null)
        stubDebouncer()

        // When
        testedListener.onDraw()

        // Then
        verifyNoInteractions(mockProcessor)
    }

    @Test
    fun `M send OrientationChanged W onDraw() { first time }`() {
        // Given
        stubDebouncer()

        // When
        testedListener.onDraw()

        // Then
        val argumentCaptor = argumentCaptor<OrientationChanged>()
        verify(mockProcessor).processScreenSnapshots(
            eq(fakeWindowsSnapshots),
            argumentCaptor.capture()
        )
        assertThat(argumentCaptor.firstValue)
            .isEqualTo(
                OrientationChanged(
                    fakeDecorWidth.densityNormalized(fakeDensity),
                    fakeDecorHeight.densityNormalized(fakeDensity)
                )
            )
    }

    @Test
    fun `M send OrientationChanged only once W onDraw() { second time }`() {
        // Given
        stubDebouncer()

        // When
        testedListener.onDraw()
        testedListener.onDraw()

        // Then
        val argumentCaptor = argumentCaptor<OrientationChanged>()
        verify(mockProcessor, times(2)).processScreenSnapshots(
            eq(fakeWindowsSnapshots),
            argumentCaptor.capture()
        )
        assertThat(argumentCaptor.firstValue)
            .isEqualTo(
                OrientationChanged(
                    fakeDecorWidth.densityNormalized(fakeDensity),
                    fakeDecorHeight.densityNormalized(fakeDensity)
                )
            )
        assertThat(argumentCaptor.secondValue).isNull()
    }

    @Test
    fun `M send OrientationChanged twice W onDraw(){called 2 times with different orientation}`() {
        // Given
        stubDebouncer()

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
        verify(mockProcessor, times(2)).processScreenSnapshots(
            eq(fakeWindowsSnapshots),
            argumentCaptor.capture()
        )
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

    @Test
    fun `M do nothing W onDraw(){ windows are empty }`() {
        // Given
        stubDebouncer()
        testedListener = WindowsOnDrawListener(
            mockActivity,
            emptyList(),
            fakeDensity,
            mockProcessor,
            mockSnapshotProducer,
            mockDebouncer
        )

        // When
        testedListener.onDraw()

        // Then
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockSnapshotProducer)
    }

    @Test
    fun `M do nothing W onDraw(){ windows lost the strong reference }`() {
        // Given
        testedListener.weakReferencedWindows.forEach { it.clear() }
        stubDebouncer()

        // When
        testedListener.onDraw()

        // Then
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockSnapshotProducer)
    }

    @Test
    fun `M do nothing W onDraw(){ owner activity lost the strong reference }`() {
        // Given
        testedListener.ownerActivityReference.clear()
        stubDebouncer()

        // When
        testedListener.onDraw()

        // Then
        verifyNoInteractions(mockProcessor)
        verifyNoInteractions(mockSnapshotProducer)
    }

    // region Internal

    private fun stubDebouncer() {
        whenever(mockDebouncer.debounce(any())).then { (it.arguments[0] as Runnable).run() }
    }

    private fun Forge.aMockedWindowsList(): List<Window> {
        return aList {
            mock {
                val mockDecorView: View = mock()
                whenever(mockDecorView.viewTreeObserver).thenReturn(mock())
                whenever(it.decorView).thenReturn(mockDecorView)
                whenever(it.callback).thenReturn(mock())
            }
        }
    }

    // endregion
}
