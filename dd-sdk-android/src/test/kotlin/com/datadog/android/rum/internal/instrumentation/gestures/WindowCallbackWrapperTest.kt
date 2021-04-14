/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.app.Application
import android.content.res.Resources
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.Window
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.ref.WeakReference
import org.assertj.core.api.Assertions.assertThat
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
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class WindowCallbackWrapperTest {

    lateinit var testedWrapper: WindowCallbackWrapper

    @Mock
    lateinit var mockCallback: Window.Callback

    @Mock
    lateinit var mockGestureDetector: GesturesDetectorWrapper

    @Mock
    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockResources: Resources

    @BeforeEach
    fun `set up`() {
        testedWrapper = WindowCallbackWrapper(
            mockCallback,
            mockGestureDetector
        )
        whenever(mockAppContext.resources).thenReturn(mockResources)
        CoreFeature.contextRef = WeakReference(mockAppContext)
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.contextRef = WeakReference(null)
    }

    @Test
    fun `dispatchTouchEvent will delegate to wrapper`(forge: Forge) {
        // Given
        val motionEvent: MotionEvent = mock()
        val spyTest = spy(testedWrapper)
        val aBoolean = forge.aBool()
        whenever(mockCallback.dispatchTouchEvent(motionEvent)).thenReturn(aBoolean)
        doReturn(motionEvent).`when`(spyTest).copyEvent(motionEvent)

        // When
        val returnedValue = spyTest.dispatchTouchEvent(motionEvent)

        // Then
        assertThat(returnedValue).isEqualTo(aBoolean)
        verify(mockCallback).dispatchTouchEvent(motionEvent)
    }

    @Test
    fun `dispatchTouchEvent will pass a copy of the event to the gesture detector`() {
        // Given
        val motionEvent: MotionEvent = mock()
        val copyMotionEvent: MotionEvent = mock()
        val spyTest = spy(testedWrapper)
        doReturn(copyMotionEvent).`when`(spyTest).copyEvent(motionEvent)

        // When
        spyTest.dispatchTouchEvent(motionEvent)

        // Then
        verify(mockGestureDetector).onTouchEvent(copyMotionEvent)
        verify(copyMotionEvent).recycle()
    }

    @Test
    fun `menu item selection will trigger a Rum UserActionEvent`(forge: Forge) {
        // Given
        val returnValue = forge.aBool()
        val itemTitle = forge.aString()
        val featureId = forge.anInt()
        val itemId = forge.anInt()
        val itemResourceName = forge.aString()
        whenever(mockResources.getResourceEntryName(itemId)).thenReturn(itemResourceName)
        val menuItem: MenuItem = mock {
            whenever(it.itemId).thenReturn(itemId)
            whenever(it.title).thenReturn(itemTitle)
        }
        whenever(mockCallback.onMenuItemSelected(featureId, menuItem)).thenReturn(returnValue)

        // When
        assertThat(testedWrapper.onMenuItemSelected(featureId, menuItem)).isEqualTo(returnValue)

        // Then
        inOrder(mockCallback, rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).addUserAction(
                eq(RumActionType.TAP),
                eq(targetName(menuItem, itemResourceName)),
                argThat {
                    val targetClassName = menuItem.javaClass.canonicalName
                    this[RumAttributes.ACTION_TARGET_CLASS_NAME] == targetClassName &&
                        this[RumAttributes.ACTION_TARGET_RESOURCE_ID] == itemResourceName &&
                        this[RumAttributes.ACTION_TARGET_TITLE] == itemTitle
                }
            )
            verify(mockCallback).onMenuItemSelected(featureId, menuItem)
        }
    }

    @Test
    fun `pressing back button will trigger specific user action event`(forge: Forge) {
        // Given
        val returnedValue = forge.aBool()
        whenever(mockCallback.dispatchKeyEvent(any())).thenReturn(returnedValue)
        val keyEvent = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)

        // When
        assertThat(testedWrapper.dispatchKeyEvent(keyEvent)).isEqualTo(returnedValue)

        // Then
        inOrder(rumMonitor.mockInstance, mockCallback) {
            verify(rumMonitor.mockInstance).addUserAction(
                RumActionType.CUSTOM,
                "back",
                emptyMap()
            )
            verify(mockCallback).dispatchKeyEvent(keyEvent)
        }
    }

    @Test
    fun `pressing back button will trigger user action event only on ACTION_UP`(forge: Forge) {
        // Given
        val returnedValue = forge.aBool()
        whenever(mockCallback.dispatchKeyEvent(any())).thenReturn(returnedValue)
        val keyEvent = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)

        // When
        assertThat(testedWrapper.dispatchKeyEvent(keyEvent)).isEqualTo(returnedValue)

        // Then
        inOrder(rumMonitor.mockInstance, mockCallback) {
            verifyZeroInteractions(rumMonitor.mockInstance)
            verify(mockCallback).dispatchKeyEvent(keyEvent)
        }
    }

    @Test
    fun `pressing any other key except back button will do nothing`(forge: Forge) {
        // Given
        val returnedValue = forge.aBool()
        whenever(mockCallback.dispatchKeyEvent(any())).thenReturn(returnedValue)
        val keyCode = forge.anInt(min = 5)
        val keyEvent = mockKeyEvent(KeyEvent.ACTION_UP, keyCode)

        // When
        assertThat(testedWrapper.dispatchKeyEvent(keyEvent)).isEqualTo(returnedValue)

        // Then
        inOrder(rumMonitor.mockInstance, mockCallback) {
            verifyZeroInteractions(rumMonitor.mockInstance)
            verify(mockCallback).dispatchKeyEvent(keyEvent)
        }
    }

    // region Internal

    private fun mockKeyEvent(action: Int, keyCode: Int): KeyEvent {
        return mock {
            whenever(it.keyCode).thenReturn(keyCode)
            whenever(it.action).thenReturn(action)
        }
    }

    // endregion

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
