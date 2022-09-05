/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Activity
import android.app.Application
import com.datadog.android.sessionreplay.recorder.Recorder
import com.datadog.android.sessionreplay.utils.RumContextProvider
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.Forgery
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
class SessionReplayLifecycleCallbackTest {

    lateinit var testedCallback: SessionReplayLifecycleCallback

    @Mock
    private lateinit var mockRecoder: Recorder

    @Mock
    private lateinit var mockRumContextProvider: RumContextProvider

    @Forgery
    private lateinit var fakePrivacy: SessionReplayPrivacy

    @BeforeEach
    fun `set up`() {
        testedCallback = SessionReplayLifecycleCallback(mockRumContextProvider, fakePrivacy)
        testedCallback.recorder = mockRecoder
    }

    @Test
    fun `M start recording activity W onActivityResumed()`() {
        // Given
        val mockActivity: Activity = mock()

        // When
        testedCallback.onActivityResumed(mockActivity)

        // Then
        verify(mockRecoder).startRecording(mockActivity)
        assertThat(testedCallback.resumedActivities).containsKey(mockActivity)
    }

    @Test
    fun `M stop recording activity W onActivityPaused() { activity previously resumed }`() {
        // Given
        val mockActivity: Activity = mock()
        testedCallback.onActivityResumed(mockActivity)

        // When
        testedCallback.onActivityPaused(mockActivity)

        // Then
        verify(mockRecoder).stopRecording(mockActivity)
        assertThat(testedCallback.resumedActivities).doesNotContainKey(mockActivity)
    }

    @Test
    fun `M stop recording activity W onActivityPaused() { activity not previously resumed }`() {
        // Given
        val mockActivity: Activity = mock()

        // When
        testedCallback.onActivityPaused(mockActivity)

        // Then
        verify(mockRecoder).stopRecording(mockActivity)
        assertThat(testedCallback.resumedActivities).doesNotContainKey(mockActivity)
    }

    @Test
    fun `M register lifecycle callback W register()`() {
        // Given
        val mockApplication: Application = mock()

        // When
        testedCallback.register(mockApplication)

        // Then
        verify(mockApplication).registerActivityLifecycleCallbacks(testedCallback)
    }

    @Test
    fun `M unregister lifecycle callback W unregisterAndStopRecorders()`() {
        // Given
        val mockApplication: Application = mock()

        // When
        testedCallback.unregisterAndStopRecorders(mockApplication)

        // Then
        verify(mockApplication).unregisterActivityLifecycleCallbacks(testedCallback)
    }

    @Test
    fun `M stop recording all resumed activities W unregisterAndStopRecorders()`() {
        // Given
        val mockApplication: Application = mock()
        val mockActivity1: Activity = mock()
        val mockActivity2: Activity = mock()
        testedCallback.onActivityResumed(mockActivity1)
        testedCallback.onActivityResumed(mockActivity2)

        // When
        testedCallback.unregisterAndStopRecorders(mockApplication)

        // Then
        verify(mockRecoder).stopRecording(mockActivity1)
        verify(mockRecoder).stopRecording(mockActivity2)
        assertThat(testedCallback.resumedActivities).isEmpty()
    }
}
