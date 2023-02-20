/*
* Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
* This product includes software developed at Datadog (https://www.datadoghq.com/).
* Copyright 2016-Present Datadog, Inc.
*/

package com.datadog.android.sessionreplay.internal.recorder.listener

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.view.View
import android.view.Window
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.Processor
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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

    @IntForgery(min = 0)
    var fakeDecorWidth: Int = 0

    @IntForgery(min = 0)
    var fakeDecorHeight: Int = 0
    var fakeOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    lateinit var fakeMockedWindows: List<Window>
    lateinit var fakeWindowsSnapshots: List<Node>

    @Mock
    lateinit var mockTheme: Theme

    @Mock
    lateinit var mockMiscUtils: MiscUtils

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockMiscUtils.resolveSystemInformation(mockActivity))
            .thenReturn(fakeSystemInformation)
        fakeMockedWindows = forge.aMockedWindowsList()
        fakeWindowsSnapshots = fakeMockedWindows.map { forge.getForgery() }
        whenever(mockActivity.theme).thenReturn(mockTheme)
        fakeMockedWindows.forEachIndexed { index, window ->
            whenever(mockSnapshotProducer.produce(window.decorView, fakeSystemInformation))
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
            mockProcessor,
            mockSnapshotProducer,
            mockDebouncer,
            mockMiscUtils
        )
    }

    @Test
    fun `M take and process snapshot W onDraw()`() {
        // Given
        stubDebouncer()

        // When
        testedListener.onDraw()

        // Then
        verify(mockProcessor).processScreenSnapshots(
            fakeWindowsSnapshots,
            fakeSystemInformation
        )
    }

    @Test
    fun `M do nothing W onDraw(){ windows are empty }`() {
        // Given
        stubDebouncer()
        testedListener = WindowsOnDrawListener(
            mockActivity,
            emptyList(),
            mockProcessor,
            mockSnapshotProducer,
            mockDebouncer
        )

        // When
        testedListener.onDraw()

        // Then
        verifyZeroInteractions(mockProcessor)
        verifyZeroInteractions(mockSnapshotProducer)
    }

    @Test
    fun `M do nothing W onDraw(){ windows lost the strong reference }`() {
        // Given
        testedListener.weakReferencedWindows.forEach { it.clear() }
        stubDebouncer()

        // When
        testedListener.onDraw()

        // Then
        verifyZeroInteractions(mockProcessor)
        verifyZeroInteractions(mockSnapshotProducer)
    }

    @Test
    fun `M do nothing W onDraw(){ owner activity lost the strong reference }`() {
        // Given
        testedListener.ownerActivityReference.clear()
        stubDebouncer()

        // When
        testedListener.onDraw()

        // Then
        verifyZeroInteractions(mockProcessor)
        verifyZeroInteractions(mockSnapshotProducer)
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
