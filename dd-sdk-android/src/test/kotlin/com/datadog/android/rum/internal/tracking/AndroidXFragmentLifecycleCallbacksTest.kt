/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.app.Dialog
import android.content.Context
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.datadog.android.core.internal.utils.resolveViewName
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class AndroidXFragmentLifecycleCallbacksTest {

    lateinit var underTest: AndroidXFragmentLifecycleCallbacks

    @Mock
    lateinit var mockFragment: Fragment

    @Mock
    lateinit var mockFragmentActivity: FragmentActivity

    @Mock
    lateinit var mockFragmentManager: FragmentManager

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockDialog: Dialog

    lateinit var attributesMap: Map<String, Any?>

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockGesturesTracker: GesturesTracker

    @BeforeEach
    fun `set up`(forge: Forge) {
        GlobalRum.registerIfAbsent(mockRumMonitor)
        RumFeature.gesturesTracker = mockGesturesTracker

        whenever(mockFragmentActivity.supportFragmentManager).thenReturn(mockFragmentManager)
        attributesMap = forge.aMap { forge.aString() to forge.aString() }
        underTest = AndroidXFragmentLifecycleCallbacks { attributesMap }
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
    }

    @Test
    fun `when fragment activity created on DialogFragment, it will register a Window Callback`(
        forge: Forge
    ) {
        val mockDialogFragment: DialogFragment = mock()
        whenever(mockDialogFragment.context) doReturn mockContext
        whenever(mockDialogFragment.dialog) doReturn mockDialog
        whenever(mockDialog.window) doReturn mockWindow

        underTest.onFragmentActivityCreated(mock(), mockDialogFragment, null)

        verify(mockGesturesTracker).startTracking(mockWindow, mockContext)
    }

    @Test
    fun `when fragment activity created on Fragment, registers nothing`(forge: Forge) {
        whenever(mockFragment.context) doReturn mockContext

        underTest.onFragmentActivityCreated(mock(), mockFragment, null)

        verifyZeroInteractions(mockGesturesTracker)
    }

    @Test
    fun `when fragment resumed it will start a view event`(forge: Forge) {
        // when
        underTest.onFragmentResumed(mock(), mockFragment)
        // then
        verify(mockRumMonitor).startView(
            eq(mockFragment),
            eq(mockFragment.resolveViewName()),
            eq(attributesMap)
        )
    }

    @Test
    fun `when fragment paused it will stop a view event`(forge: Forge) {
        // when
        underTest.onFragmentPaused(mock(), mockFragment)
        // then
        verify(mockRumMonitor).stopView(
            eq(mockFragment),
            eq(emptyMap())
        )
    }

    @Test
    fun `will register the callback to fragment manager when required`() {
        // when
        underTest.register(mockFragmentActivity)

        // then
        verify(mockFragmentManager).registerFragmentLifecycleCallbacks(underTest, true)
    }

    @Test
    fun `will unregister the callback from the fragment manager when required`() {
        // when
        underTest.unregister(mockFragmentActivity)

        // then
        verify(mockFragmentManager).unregisterFragmentLifecycleCallbacks(underTest)
    }
}
