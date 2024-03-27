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
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.recorder.Debouncer
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.IS_SUCCESSFUL
import com.datadog.android.sessionreplay.internal.recorder.telemetry.MethodCalledTelemetry.Companion.METHOD_CALLED_METRIC_NAME
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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

    private lateinit var testedListener: WindowsOnDrawListener

    @Mock
    lateinit var mockDecorView: View

    private lateinit var mockResources: Resources
    private lateinit var configuration: Configuration

    @Mock
    lateinit var mockSnapshotProducer: SnapshotProducer

    @Mock
    lateinit var mockRecordedDataQueueHandler: RecordedDataQueueHandler

    @Mock
    lateinit var mockDebouncer: Debouncer

    @IntForgery(min = 0)
    var fakeDecorWidth: Int = 0

    @IntForgery(min = 0)
    var fakeDecorHeight: Int = 0
    private var fakeOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    private lateinit var fakeMockedDecorViews: List<View>
    private lateinit var fakeWindowsSnapshots: List<Node>

    @Mock
    lateinit var mockTheme: Theme

    @Mock
    lateinit var mockMiscUtils: MiscUtils

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @Forgery
    lateinit var fakeSnapshotQueueItem: SnapshotRecordedDataQueueItem

    @Mock
    lateinit var mockContext: Context

    @Forgery
    lateinit var fakePrivacy: SessionReplayPrivacy

    @Mock
    lateinit var mockLogger: InternalLogger

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockMiscUtils.resolveSystemInformation(mockContext))
            .thenReturn(fakeSystemInformation)
        fakeMockedDecorViews = forge.aMockedDecorViewList().onEach {
            whenever(it.context).thenReturn(mockContext)
        }
        fakeWindowsSnapshots = fakeMockedDecorViews.map { forge.getForgery() }
        whenever(mockContext.theme).thenReturn(mockTheme)
        fakeMockedDecorViews.forEachIndexed { index, decorView ->
            whenever(
                mockSnapshotProducer.produce(
                    eq(decorView),
                    eq(fakeSystemInformation),
                    eq(fakePrivacy),
                    any()
                )
            )
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
            zOrderedDecorViews = fakeMockedDecorViews,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            privacy = fakePrivacy,
            debouncer = mockDebouncer,
            miscUtils = mockMiscUtils,
            logger = mockLogger
        )
    }

    @Test
    fun `M take and add to queue W onDraw()`() {
        // Given
        stubDebouncer()

        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)

        // When
        testedListener.onDraw()

        // Then
        verify(mockRecordedDataQueueHandler).addSnapshotItem(fakeSystemInformation)
    }

    @Test
    fun `M update queue with correct nodes W onDraw()`() {
        // Given
        stubDebouncer()

        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)

        fakeSnapshotQueueItem.pendingJobs.set(0)

        // When
        testedListener.onDraw()

        // Then
        val argCaptor = argumentCaptor<RecordedDataQueueRefs>()
        verify(mockSnapshotProducer, times(fakeWindowsSnapshots.size)).produce(
            rootView = any(),
            systemInformation = any(),
            privacy = eq(fakePrivacy),
            recordedDataQueueRefs = argCaptor.capture()
        )
        assertThat(argCaptor.firstValue.recordedDataQueueItem).isEqualTo(fakeSnapshotQueueItem)
        verify(mockRecordedDataQueueHandler).tryToConsumeItems()
    }

    @Test
    fun `M do nothing W onDraw(){ windows are empty }`() {
        // Given
        stubDebouncer()
        testedListener = WindowsOnDrawListener(
            zOrderedDecorViews = emptyList(),
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            privacy = fakePrivacy,
            debouncer = mockDebouncer,
            logger = mockLogger
        )

        // When
        testedListener.onDraw()

        // Then
        verifyNoInteractions(mockRecordedDataQueueHandler)
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
        verify(mockRecordedDataQueueHandler, never()).tryToConsumeItems()
    }

    @Test
    fun `M do nothing W onDraw(){ no available view context }`() {
        // Given
        fakeMockedDecorViews.forEach { whenever(it.context).thenReturn(null) }
        stubDebouncer()

        // When
        testedListener.onDraw()

        // Then
        verify(mockRecordedDataQueueHandler, never()).tryToConsumeItems()
    }

    @Test
    fun `M call methodCall telemetry with true W onDraw() { has nodes }`() {
        // Given
        testedListener = WindowsOnDrawListener(
            zOrderedDecorViews = fakeMockedDecorViews,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            debouncer = mockDebouncer,
            miscUtils = mockMiscUtils,
            logger = mockLogger,
            methodCallTelemetrySamplingRate = 100f
        )

        stubDebouncer()

        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)

        fakeSnapshotQueueItem.pendingJobs.set(0)

        // When
        testedListener.onDraw()

        // Then
        val metricTitleCaptor = argumentCaptor<() -> String>()
        val additionalPropertiesCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockLogger).logMetric(metricTitleCaptor.capture(), additionalPropertiesCaptor.capture())
        metricTitleCaptor.firstValue.run {
            assertThat(this()).isEqualTo(METHOD_CALLED_METRIC_NAME)
        }
        val isSuccessful = additionalPropertiesCaptor.firstValue[IS_SUCCESSFUL] as Boolean
        assertThat(isSuccessful).isTrue()
    }

    @Test
    fun `M send methodCall telemetry with false W onDraw() { no nodes }`() {
        // Given
        testedListener = WindowsOnDrawListener(
            zOrderedDecorViews = fakeMockedDecorViews,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            snapshotProducer = mockSnapshotProducer,
            debouncer = mockDebouncer,
            miscUtils = mockMiscUtils,
            logger = mockLogger,
            methodCallTelemetrySamplingRate = 100f
        )

        stubDebouncer()

        whenever(mockSnapshotProducer.produce(any(), any(), any(), any())).thenReturn(null)

        whenever(mockRecordedDataQueueHandler.addSnapshotItem(any<SystemInformation>()))
            .thenReturn(fakeSnapshotQueueItem)

        fakeSnapshotQueueItem.pendingJobs.set(0)

        // When
        testedListener.onDraw()

        // Then
        val metricTitleCaptor = argumentCaptor<() -> String>()
        val additionalPropertiesCaptor = argumentCaptor<Map<String, Any>>()
        verify(mockLogger).logMetric(metricTitleCaptor.capture(), additionalPropertiesCaptor.capture())
        metricTitleCaptor.firstValue.run {
            assertThat(this()).isEqualTo(METHOD_CALLED_METRIC_NAME)
        }
        val isSuccessful = additionalPropertiesCaptor.firstValue[IS_SUCCESSFUL] as Boolean
        assertThat(isSuccessful).isFalse()
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
