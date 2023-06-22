/*
* Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
* This product includes software developed at Datadog (https://www.datadoghq.com/).
* Copyright 2016-Present Datadog, Inc.
*/

package com.datadog.android.sessionreplay.internal.recorder.listener

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.Processor
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
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
    lateinit var mockDecorView: View
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

    lateinit var fakeMockedDecorViews: List<View>
    lateinit var fakeWindowsSnapshots: List<Node>

    @Mock
    lateinit var mockTheme: Theme

    @Mock
    lateinit var mockMiscUtils: MiscUtils

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @Mock
    lateinit var mockContext: Context

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockMiscUtils.resolveSystemInformation(mockContext))
            .thenReturn(fakeSystemInformation)
        fakeMockedDecorViews = forge.aMockedDecorViewList()
        fakeWindowsSnapshots = fakeMockedDecorViews.map { forge.getForgery() }
        whenever(mockContext.theme).thenReturn(mockTheme)
        fakeMockedDecorViews.forEachIndexed { index, decorView ->
            whenever(mockSnapshotProducer.produce(decorView, fakeSystemInformation))
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
        configuration.orientation = fakeOrientation
        mockResources = mock {
            whenever(it.configuration).thenReturn(configuration)
        }
        whenever(mockContext.resources).thenReturn(mockResources)
        testedListener = WindowsOnDrawListener(
            mockContext,
            fakeMockedDecorViews,
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
            mockContext,
            emptyList(),
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
        testedListener.weakReferencedDecorViews.forEach { it.clear() }
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

    private fun Forge.aMockedDecorViewList(): List<View> {
        return aList {
            mock {
                whenever(it.viewTreeObserver).thenReturn(mock())
            }
        }
    }

    // endregion
}