/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Activity
import android.view.View
import android.view.Window
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import com.datadog.android.sessionreplay.internal.recorder.callback.OnWindowRefreshedCallback
import com.datadog.android.sessionreplay.internal.recorder.callback.RecorderFragmentLifecycleCallback
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
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
internal class SessionReplayLifecycleCallbackTest {

    lateinit var testedCallback: SessionReplayLifecycleCallback

    @Mock
    private lateinit var mockActivity: Activity

    @Mock
    private lateinit var mockWindow: Window

    @Mock
    private lateinit var mockDecorView: View

    @Mock
    private lateinit var mockOnWindowRefreshedCallback: OnWindowRefreshedCallback

    @BeforeEach
    fun `set up`() {
        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(mockWindow.decorView).thenReturn(mockDecorView)
        testedCallback = SessionReplayLifecycleCallback(
            mockOnWindowRefreshedCallback
        )
    }

    @Test
    fun `M register fragment lifecycle W onActivityCreated(){FragmentActivity}`() {
        // Given
        val mockFragmentManager: FragmentManager = mock()
        val mockFragmentActivity: FragmentActivity = mock {
            whenever(it.supportFragmentManager).thenReturn(mockFragmentManager)
        }

        // When
        testedCallback.onActivityCreated(mockFragmentActivity, mock())

        // Then
        val argumentCaptor = argumentCaptor<FragmentLifecycleCallbacks>()
        verify(mockFragmentManager).registerFragmentLifecycleCallbacks(
            argumentCaptor.capture(),
            eq(true)
        )
        assertThat(argumentCaptor.firstValue)
            .isInstanceOf(RecorderFragmentLifecycleCallback::class.java)
    }

    @Test
    fun `M do nothing W onActivityCreated(){no FragmentActivity}`() {
        // When
        testedCallback.onActivityCreated(mockActivity, mock())

        // Then
        verifyNoInteractions(mockActivity)
    }

    @Test
    fun `M keep window as active callback W onActivityResumed()`() {
        // When
        testedCallback.onActivityResumed(mockActivity)

        // Then
        assertThat(testedCallback.getCurrentWindows()).isEqualTo(listOf(mockWindow))
    }

    @Test
    fun `M notify callback W onActivityResumed()`() {
        // When
        testedCallback.onActivityResumed(mockActivity)

        // Then
        verify(mockOnWindowRefreshedCallback).onWindowsAdded(eq(listOf(mockWindow)))
    }

    @Test
    fun `M keep windows as active callback W onActivityResumed { multiple times }()`(forge: Forge) {
        // Given
        val mockActivities = forge.aList {
            mock<Activity> {
                whenever(it.window).thenReturn(mock())
            }
        }
        val expectedWindows = mockActivities.map { it.window }

        // When
        mockActivities.forEach {
            testedCallback.onActivityResumed(it)
        }

        // Then
        assertThat(testedCallback.getCurrentWindows())
            .containsExactlyInAnyOrder(*expectedWindows.toTypedArray())
    }

    @Test
    fun `M drop Window from currentlyActive onActivityPaused() {activity previously resumed}`() {
        // Given
        testedCallback.onActivityResumed(mockActivity)

        // When
        testedCallback.onActivityPaused(mockActivity)

        // Then
        assertThat(testedCallback.getCurrentWindows()).isEmpty()
    }

    @Test
    fun `M drop Window from currentlyActive onActivityPaused() {multiple activities}`(
        forge: Forge
    ) {
        // Given
        val mockActivities = forge.aList {
            mock<Activity> {
                whenever(it.window).thenReturn(mock())
            }
        }
        mockActivities.forEach {
            testedCallback.onActivityResumed(it)
        }

        // When
        mockActivities.forEach {
            testedCallback.onActivityPaused(it)
        }

        // Then
        assertThat(testedCallback.getCurrentWindows()).isEmpty()
    }

    @Test
    fun `M notify callback W onActivityPaused()`() {
        // When
        testedCallback.onActivityPaused(mockActivity)

        // Then
        verify(mockOnWindowRefreshedCallback).onWindowsRemoved(listOf(mockWindow))
    }

    @Test
    fun `M do nothing onActivityResumed {activity has no window}`() {
        // Given
        whenever(mockActivity.window).thenReturn(null)

        // When
        testedCallback.onActivityResumed(mockActivity)

        // Then
        verifyNoInteractions(mockOnWindowRefreshedCallback)
    }
}
