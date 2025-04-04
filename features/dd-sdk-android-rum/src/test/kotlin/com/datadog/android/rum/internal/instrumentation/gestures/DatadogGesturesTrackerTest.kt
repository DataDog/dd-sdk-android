/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.app.Activity
import android.view.View
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.internal.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpActionTrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogGesturesTrackerTest : ObjectTest<DatadogGesturesTracker>() {

    lateinit var testedTracker: DatadogGesturesTracker

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockInteractionPredicate: InteractionPredicate

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockGestureDetector: GesturesDetectorWrapper

    @Mock
    lateinit var mockActionTrackingStrategy: ActionTrackingStrategy

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @BeforeEach
    fun `set up`() {
        testedTracker =
            DatadogGesturesTracker(
                emptyArray(),
                mockInteractionPredicate,
                mockActionTrackingStrategy,
                mockInternalLogger
            )
        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
    }

    override fun createInstance(forge: Forge): DatadogGesturesTracker {
        return DatadogGesturesTracker(
            forge.aList { StubViewAttributesProvider(anAlphabeticalString()) }.toTypedArray(),
            NoOpInteractionPredicate(),
            NoOpActionTrackingStrategy(),
            mockInternalLogger
        )
    }

    override fun createEqualInstance(
        source: DatadogGesturesTracker,
        forge: Forge
    ): DatadogGesturesTracker {
        return DatadogGesturesTracker(
            source.targetAttributesProviders.map {
                check(it is StubViewAttributesProvider)
                StubViewAttributesProvider(it.name)
            }.toTypedArray(),
            NoOpInteractionPredicate(),
            NoOpActionTrackingStrategy(),
            mockInternalLogger
        )
    }

    override fun createUnequalInstance(
        source: DatadogGesturesTracker,
        forge: Forge
    ): DatadogGesturesTracker? {
        return DatadogGesturesTracker(
            source.targetAttributesProviders.map {
                check(it is StubViewAttributesProvider)
                StubViewAttributesProvider(it.name + forge.aNumericalString())
            }.toTypedArray(),
            StubInteractionPredicate(),
            mockActionTrackingStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `will start tracking the activity`() {
        // When
        val spyTest = spy(testedTracker)
        doReturn(mockGestureDetector)
            .whenever(spyTest)
            .generateGestureDetector(mockActivity, mockWindow, mockSdkCore)
        spyTest.startTracking(mockWindow, mockActivity, mockSdkCore)

        // Then
        val argumentCaptor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = argumentCaptor.capture()
        assertThat((argumentCaptor.firstValue as WindowCallbackWrapper).interactionPredicate)
            .isEqualTo(mockInteractionPredicate)
    }

    @Test
    fun `will stop tracking the activity`() {
        // Given
        whenever(mockWindow.callback)
            .thenReturn(
                WindowCallbackWrapper(
                    mockWindow,
                    mockSdkCore,
                    NoOpWindowCallback(),
                    mockGestureDetector,
                    internalLogger = mockInternalLogger
                )
            )

        // When
        testedTracker.stopTracking(mockWindow, mockActivity)

        // Then
        verify(mockWindow).callback = null
    }

    @Test
    fun `stop tracking the activity will restore the previous callback if was not null`() {
        // Given
        val previousCallback: Window.Callback = mock()
        whenever(mockWindow.callback)
            .thenReturn(
                WindowCallbackWrapper(
                    mockWindow,
                    mockSdkCore,
                    previousCallback,
                    mockGestureDetector,
                    internalLogger = mockInternalLogger
                )
            )

        // When
        testedTracker.stopTracking(mockWindow, mockActivity)

        // Then
        verify(mockWindow).callback = previousCallback
    }

    @Test
    fun `stop will do nothing if the activity was not tracked`() {
        // When
        testedTracker.stopTracking(mockWindow, mockActivity)

        // Then
        verify(mockWindow, never()).callback = any()
    }

    @Test
    fun `will not track an activity with no decor view`() {
        // Given
        whenever(mockWindow.decorView).thenReturn(null)

        // Then
        verify(mockWindow, never()).callback = any()
    }

    data class StubViewAttributesProvider(
        val name: String
    ) : ViewAttributesProvider {
        override fun extractAttributes(view: View, attributes: MutableMap<String, Any?>) {
            attributes[name] = view.toString()
        }
    }

    class StubInteractionPredicate : InteractionPredicate {
        override fun getTargetName(target: Any): String? {
            return null
        }
    }
}
