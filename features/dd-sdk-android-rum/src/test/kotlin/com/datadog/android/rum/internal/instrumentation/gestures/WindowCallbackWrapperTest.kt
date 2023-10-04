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
import android.view.View
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.jvm.internal.Intrinsics

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

    @Mock
    lateinit var mockMotionEvent: MotionEvent

    @Mock
    lateinit var mockCopiedMotionEvent: MotionEvent

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedWrapper = WindowCallbackWrapper(
            mockWindow,
            rumMonitor.mockSdkCore,
            mockCallback,
            mockGestureDetector,
            copyEvent = { mockCopiedMotionEvent },
            internalLogger = mockInternalLogger
        )
        whenever(mockAppContext.resources).thenReturn(mockResources)
        whenever(mockWindow.context).thenReturn(mockAppContext)
    }

    // region dispatchTouchEvent

    @Test
    fun `𝕄 delegate event to wrapped callback 𝕎 dispatchTouchEvent()`(
        @BoolForgery wrappedResult: Boolean
    ) {
        // Given
        whenever(mockCallback.dispatchTouchEvent(mockMotionEvent)).thenReturn(wrappedResult)

        // When
        val returnedValue = testedWrapper.dispatchTouchEvent(mockMotionEvent)

        // Then
        assertThat(returnedValue).isEqualTo(wrappedResult)
        verify(mockCallback).dispatchTouchEvent(mockMotionEvent)
    }

    @Test
    fun `𝕄 send copy of event to gesture detector 𝕎 dispatchTouchEvent()`() {
        // When
        testedWrapper.dispatchTouchEvent(mockMotionEvent)

        // Then
        verify(mockGestureDetector).onTouchEvent(mockCopiedMotionEvent)
        verify(mockCopiedMotionEvent).recycle()
    }

    @Test
    fun `𝕄 not call gesture detector 𝕎 dispatchTouchEvent() {null event}`() {
        // When
        testedWrapper.dispatchTouchEvent(null)

        // Then
        verifyNoInteractions(mockGestureDetector)
        verify(mockCallback).dispatchTouchEvent(null)
    }

    @Test
    fun `𝕄 prevent crash 𝕎 dispatchTouchEvent() {wrapped callback throws null parameter exception}`() {
        // Given
        whenever(mockCallback.dispatchTouchEvent(mockMotionEvent)).thenAnswer {
            Intrinsics.checkNotNullParameter(null, "event")
        }

        // When
        val returnedValue = testedWrapper.dispatchTouchEvent(mockMotionEvent)

        // Then
        assertThat(returnedValue).isTrue()
        verify(mockCallback).dispatchTouchEvent(mockMotionEvent)
    }

    @Test
    fun `𝕄 propagate exception 𝕎 dispatchTouchEvent() {wrapped callback throws exception}`(
        @Forgery exception: Exception
    ) {
        // Given
        whenever(mockCallback.dispatchTouchEvent(mockMotionEvent)).thenThrow(exception)

        // When + Then
        val exceptionCaught =
            assertThrows<Exception> { testedWrapper.dispatchTouchEvent(mockMotionEvent) }
        assertThat(exceptionCaught).isSameAs(exception)
    }

    // endregion

    // region onMenuItemSelected

    @Test
    fun `M trigger RUM Action with custom name W onMenuItemSelected() {custom target not empty}`(
        @StringForgery itemTitle: String,
        @StringForgery itemResourceName: String,
        @StringForgery customTargetName: String,
        @IntForgery itemId: Int,
        @IntForgery featureId: Int
    ) {
        // Given
        whenever(mockResources.getResourceEntryName(itemId)).thenReturn(itemResourceName)
        val menuItem: MenuItem = mock {
            whenever(it.itemId).thenReturn(itemId)
            whenever(it.title).thenReturn(itemTitle)
        }
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(menuItem)).thenReturn(customTargetName)
        }
        testedWrapper = WindowCallbackWrapper(
            mockWindow,
            rumMonitor.mockSdkCore,
            mockCallback,
            mockGestureDetector,
            mockInteractionPredicate,
            internalLogger = mockInternalLogger
        )

        // When
        testedWrapper.onMenuItemSelected(featureId, menuItem)

        // Then
        inOrder(mockCallback, rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).addAction(
                eq(RumActionType.TAP),
                eq(customTargetName),
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
    fun `M trigger RUM Action with empty name W onMenuItemSelected() { custom target empty }`(
        @StringForgery itemTitle: String,
        @StringForgery itemResourceName: String,
        @IntForgery itemId: Int,
        @IntForgery featureId: Int
    ) {
        // Given
        whenever(mockResources.getResourceEntryName(itemId)).thenReturn(itemResourceName)
        val menuItem: MenuItem = mock {
            whenever(it.itemId).thenReturn(itemId)
            whenever(it.title).thenReturn(itemTitle)
        }
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(menuItem)).thenReturn("")
        }
        testedWrapper = WindowCallbackWrapper(
            mockWindow,
            rumMonitor.mockSdkCore,
            mockCallback,
            mockGestureDetector,
            mockInteractionPredicate,
            internalLogger = mockInternalLogger
        )

        // When
        testedWrapper.onMenuItemSelected(featureId, menuItem)

        // Then
        inOrder(mockCallback, rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).addAction(
                eq(RumActionType.TAP),
                eq(""),
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
    fun `M trigger RUM Action with empty name W onMenuItemSelected() { custom target null }`(
        @StringForgery itemTitle: String,
        @StringForgery itemResourceName: String,
        @IntForgery itemId: Int,
        @IntForgery featureId: Int
    ) {
        // Given
        whenever(mockResources.getResourceEntryName(itemId)).thenReturn(itemResourceName)
        val menuItem: MenuItem = mock {
            whenever(it.itemId).thenReturn(itemId)
            whenever(it.title).thenReturn(itemTitle)
        }
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(menuItem)).thenReturn(null)
        }
        testedWrapper = WindowCallbackWrapper(
            mockWindow,
            rumMonitor.mockSdkCore,
            mockCallback,
            mockGestureDetector,
            mockInteractionPredicate,
            internalLogger = mockInternalLogger
        )

        // When
        testedWrapper.onMenuItemSelected(featureId, menuItem)

        // Then
        inOrder(mockCallback, rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).addAction(
                eq(RumActionType.TAP),
                eq(""),
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
    fun `𝕄 delegate event to wrapped callback 𝕎 onMenuItemSelected()`(
        @StringForgery itemTitle: String,
        @IntForgery itemId: Int,
        @IntForgery featureId: Int,
        @BoolForgery wrappedResult: Boolean
    ) {
        // Given
        val menuItem: MenuItem = mock {
            whenever(it.itemId).thenReturn(itemId)
            whenever(it.title).thenReturn(itemTitle)
        }
        whenever(mockCallback.onMenuItemSelected(featureId, menuItem)).thenReturn(wrappedResult)

        // When
        val returnedValue = testedWrapper.onMenuItemSelected(featureId, menuItem)

        // Then
        assertThat(returnedValue).isEqualTo(wrappedResult)
        verify(mockCallback).onMenuItemSelected(featureId, menuItem)
    }

    @Test
    fun `𝕄 propagate exception 𝕎 onMenuItemSelected() {wrapped callback throws exception}`(
        @StringForgery itemTitle: String,
        @IntForgery itemId: Int,
        @IntForgery featureId: Int,
        @Forgery exception: Exception
    ) {
        // Given
        val menuItem: MenuItem = mock {
            whenever(it.itemId).thenReturn(itemId)
            whenever(it.title).thenReturn(itemTitle)
        }
        whenever(mockCallback.onMenuItemSelected(featureId, menuItem)).thenThrow(exception)

        // When + Then
        val exceptionCaught =
            assertThrows<Exception> { testedWrapper.onMenuItemSelected(featureId, menuItem) }
        assertThat(exceptionCaught).isSameAs(exception)
    }

    @Test
    fun `𝕄 prevent crash 𝕎 onMenuItemSelected() {wrapped callback throws null parameter exception}`(
        @StringForgery itemTitle: String,
        @IntForgery itemId: Int,
        @IntForgery featureId: Int
    ) {
        // Given
        val menuItem: MenuItem = mock {
            whenever(it.itemId).thenReturn(itemId)
            whenever(it.title).thenReturn(itemTitle)
        }
        whenever(
            mockCallback.onMenuItemSelected(
                featureId,
                menuItem
            )
        ).thenAnswer { Intrinsics.checkNotNullParameter(null, "event") }

        // When
        val returnedValue = testedWrapper.onMenuItemSelected(featureId, menuItem)

        // Then
        assertThat(returnedValue).isTrue()
        verify(mockCallback).onMenuItemSelected(featureId, menuItem)
    }

    // endregion

    // region dispatchKeyEvent

    @Test
    fun `𝕄 not trigger RUM action 𝕎 dispatchKeyEvent() { DOWN-BACK}`() {
        // Given
        val keyEvent = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)

        // When
        testedWrapper.dispatchKeyEvent(keyEvent)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `𝕄 not trigger RUM action 𝕎 dispatchKeyEvent() { DOWN-ANY}`(
        @IntForgery(min = KeyEvent.KEYCODE_CALL) keyCode: Int
    ) {
        // Given
        val keyEvent = mockKeyEvent(KeyEvent.ACTION_DOWN, keyCode)

        // When
        testedWrapper.dispatchKeyEvent(keyEvent)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `𝕄 not trigger RUM action 𝕎 dispatchKeyEvent() { UP-ANY}`(
        @IntForgery(min = KeyEvent.KEYCODE_CALL) keyCode: Int
    ) {
        // Given
        val keyEvent = mockKeyEvent(KeyEvent.ACTION_UP, keyCode)

        // When
        testedWrapper.dispatchKeyEvent(keyEvent)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `𝕄 not trigger RUM action 𝕎 dispatchKeyEvent() {keyEvent=null}`() {
        // When
        testedWrapper.dispatchKeyEvent(null)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(
                InternalLogger.Target.MAINTAINER,
                InternalLogger.Target.TELEMETRY
            ),
            "Received null KeyEvent",
            null
        )
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `𝕄 trigger RUM action 𝕎 dispatchKeyEvent() { UP-BACK, custom name null}`() {
        // Given
        val keyEvent = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(keyEvent)).thenReturn(null)
        }

        testedWrapper = WindowCallbackWrapper(
            mockWindow,
            rumMonitor.mockSdkCore,
            mockCallback,
            mockGestureDetector,
            mockInteractionPredicate,
            internalLogger = mockInternalLogger
        )

        // When
        testedWrapper.dispatchKeyEvent(keyEvent)

        // Then
        verify(rumMonitor.mockInstance).addAction(
            RumActionType.BACK,
            WindowCallbackWrapper.BACK_DEFAULT_TARGET_NAME,
            emptyMap()
        )
    }

    @Test
    fun `𝕄 trigger RUM action 𝕎 dispatchKeyEvent() { UP-BACK, custom name empty}`() {
        // Given
        val keyEvent = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(keyEvent)).thenReturn("")
        }

        testedWrapper = WindowCallbackWrapper(
            mockWindow,
            rumMonitor.mockSdkCore,
            mockCallback,
            mockGestureDetector,
            mockInteractionPredicate,
            internalLogger = mockInternalLogger
        )

        // When
        testedWrapper.dispatchKeyEvent(keyEvent)

        // Then
        verify(rumMonitor.mockInstance).addAction(
            RumActionType.BACK,
            WindowCallbackWrapper.BACK_DEFAULT_TARGET_NAME,
            emptyMap()
        )
    }

    @Test
    fun `𝕄 trigger RUM action with custom name 𝕎 dispatchKeyEvent() { UP-BACK, custom name}`(
        @StringForgery customTargetName: String
    ) {
        // Given
        val keyEvent = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(keyEvent)).thenReturn(customTargetName)
        }
        testedWrapper = WindowCallbackWrapper(
            mockWindow,
            rumMonitor.mockSdkCore,
            mockCallback,
            mockGestureDetector,
            mockInteractionPredicate,
            internalLogger = mockInternalLogger
        )

        // When
        testedWrapper.dispatchKeyEvent(keyEvent)

        // Then
        verify(rumMonitor.mockInstance).addAction(
            RumActionType.BACK,
            customTargetName,
            emptyMap()
        )
    }

    @Test
    fun `𝕄 delegate event to wrapped callback 𝕎 dispatchKeyEvent()`(
        @IntForgery action: Int,
        @IntForgery keyCode: Int,
        @BoolForgery wrappedResult: Boolean
    ) {
        // Given
        val keyEvent = mockKeyEvent(action, keyCode)
        whenever(mockCallback.dispatchKeyEvent(keyEvent)).thenReturn(wrappedResult)

        // When
        val returnedValue = testedWrapper.dispatchKeyEvent(keyEvent)

        // Then
        assertThat(returnedValue).isEqualTo(wrappedResult)
        verify(mockCallback).dispatchKeyEvent(keyEvent)
    }

    @Test
    fun `𝕄 delegate event to wrapped callback 𝕎 dispatchKeyEvent() {keyEvent=null}`(
        @BoolForgery wrappedResult: Boolean
    ) {
        // Given
        whenever(mockCallback.dispatchKeyEvent(null)).thenReturn(wrappedResult)

        // When
        val returnedValue = testedWrapper.dispatchKeyEvent(null)

        // Then
        assertThat(returnedValue).isEqualTo(wrappedResult)
        verify(mockCallback).dispatchKeyEvent(null)
    }

    @Test
    fun `𝕄 prevent crash 𝕎 dispatchKeyEvent() {wrapped callback throws null parameter exception}`(
        @IntForgery action: Int,
        @IntForgery keyCode: Int
    ) {
        // Given
        val keyEvent = mockKeyEvent(action, keyCode)
        whenever(mockCallback.dispatchKeyEvent(keyEvent)).thenAnswer {
            Intrinsics.checkNotNullParameter(null, "event")
        }

        // When
        val returnedValue = testedWrapper.dispatchKeyEvent(keyEvent)

        // Then
        assertThat(returnedValue).isTrue()
        verify(mockCallback).dispatchKeyEvent(keyEvent)
    }

    @Test
    fun `𝕄 propagate exception 𝕎 dispatchKeyEvent() {wrapped callback throws exception}`(
        @IntForgery action: Int,
        @IntForgery keyCode: Int,
        @Forgery exception: Exception
    ) {
        // Given
        val keyEvent = mockKeyEvent(action, keyCode)
        whenever(mockCallback.dispatchKeyEvent(keyEvent)).thenThrow(exception)

        // When + Then
        val exceptionCaught = assertThrows<Exception> { testedWrapper.dispatchKeyEvent(keyEvent) }
        assertThat(exceptionCaught).isSameAs(exception)
    }

    @Test
    fun `M send CLICK event W dispatchKeyEvent() {  KEYCODE_DPAD_CENTER }`() {
        // Given
        val mockKeyEvent = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        val fakeFocusedView = View(mock())
        whenever(mockWindow.currentFocus).thenReturn(fakeFocusedView)

        // When
        testedWrapper.dispatchKeyEvent(mockKeyEvent)

        // Then
        verify(rumMonitor.mockInstance).addAction(
            RumActionType.CLICK,
            "",
            mapOf(
                RumAttributes.ACTION_TARGET_CLASS_NAME to
                    fakeFocusedView.targetClassName(),
                RumAttributes.ACTION_TARGET_RESOURCE_ID to
                    mockWindow.context.resourceIdName(fakeFocusedView.id)
            )
        )
    }

    @Test
    fun `M send CLICK event W dispatchKeyEvent() {KEYCODE_DPAD_CENTER, custom target name }`(
        @StringForgery fakeCustomTargetName: String
    ) {
        // Given
        val mockKeyEvent = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        val fakeFocusedView = View(mock())
        whenever(mockWindow.currentFocus).thenReturn(fakeFocusedView)
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(fakeFocusedView)).thenReturn(fakeCustomTargetName)
        }
        testedWrapper = WindowCallbackWrapper(
            mockWindow,
            rumMonitor.mockSdkCore,
            mockCallback,
            mockGestureDetector,
            mockInteractionPredicate,
            internalLogger = mockInternalLogger
        )

        // When
        testedWrapper.dispatchKeyEvent(mockKeyEvent)

        // Then
        verify(rumMonitor.mockInstance).addAction(
            RumActionType.CLICK,
            fakeCustomTargetName,
            mapOf(
                RumAttributes.ACTION_TARGET_CLASS_NAME to
                    fakeFocusedView.targetClassName(),
                RumAttributes.ACTION_TARGET_RESOURCE_ID to
                    mockWindow.context.resourceIdName(fakeFocusedView.id)
            )
        )
    }

    @Test
    fun `M do nothing W dispatchKeyEvent() {  KEYCODE_DPAD_CENTER, ACTION_DOWN }`() {
        // Given
        val mockKeyEvent = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        val fakeFocusedView = View(mock())
        whenever(mockWindow.currentFocus).thenReturn(fakeFocusedView)

        // When
        testedWrapper.dispatchKeyEvent(mockKeyEvent)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M do nothing W dispatchKeyEvent() {  KEYCODE_DPAD_CENTER, window reference is null }`() {
        // Given
        val mockKeyEvent = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        val fakeFocusedView = View(mock())
        whenever(mockWindow.currentFocus).thenReturn(fakeFocusedView)
        testedWrapper.windowReference.clear()

        // When
        testedWrapper.dispatchKeyEvent(mockKeyEvent)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M do nothing W dispatchKeyEvent() {  KEYCODE_DPAD_CENTER, no focused view }`() {
        // Given
        val mockKeyEvent = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        whenever(mockWindow.currentFocus).thenReturn(null)

        // When
        testedWrapper.dispatchKeyEvent(mockKeyEvent)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M apply the custom attributes providers W dispatchKeyEvent()`(forge: Forge) {
        // Given
        val mockKeyEvent = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        val fakeFocusedView = View(mock())
        val mockAttributesProviders = forge.aList(size = 10) {
            mock<ViewAttributesProvider>()
        }.toTypedArray()
        whenever(mockWindow.currentFocus).thenReturn(fakeFocusedView)

        // When
        testedWrapper = WindowCallbackWrapper(
            mockWindow,
            rumMonitor.mockSdkCore,
            mockCallback,
            mockGestureDetector,
            copyEvent = { mockCopiedMotionEvent },
            targetAttributesProviders = mockAttributesProviders,
            internalLogger = mockInternalLogger
        )
        testedWrapper.dispatchKeyEvent(mockKeyEvent)

        // Then
        mockAttributesProviders.forEach {
            verify(it).extractAttributes(
                eq(fakeFocusedView),
                argThat {
                    this.containsKey(RumAttributes.ACTION_TARGET_CLASS_NAME) &&
                        this.containsKey(RumAttributes.ACTION_TARGET_RESOURCE_ID)
                }
            )
        }
    }

    // endregion

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
