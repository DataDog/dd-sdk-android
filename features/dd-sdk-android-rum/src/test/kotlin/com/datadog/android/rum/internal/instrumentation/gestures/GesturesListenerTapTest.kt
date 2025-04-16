/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.ui.platform.ComposeView
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.toHexString
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesListenerScrollSwipeTest.ScrollableListView
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.rum.tracking.ViewTarget
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class GesturesListenerTapTest : AbstractGesturesListenerTest() {

    @Test
    fun `M return true W call onSingleTap()`(forge: Forge) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        val result = testedListener.onSingleTapUp(mockEvent)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `onTap sends the right target when the ViewGroup and its child are both clickable`(
        forge: Forge
    ) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        val container1: ViewGroup = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        )
        val target: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        val notClickableInvalidTarget: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        )
        val notVisibleInvalidTarget: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            visible = false,
            forge = forge
        )
        val container2: ViewGroup = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        ) {
            whenever(it.childCount).thenReturn(3)
            whenever(it.getChildAt(0)).thenReturn(notClickableInvalidTarget)
            whenever(it.getChildAt(1)).thenReturn(notVisibleInvalidTarget)
            whenever(it.getChildAt(2)).thenReturn(target)
        }
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(container1)
            whenever(it.getChildAt(1)).thenReturn(container2)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(target, expectedResourceName)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(target, "", expectedResourceName)
    }

    @Test
    fun `onTap dispatches an UserAction if target is ViewGroup and clickable`(forge: Forge) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        val target: ViewGroup = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(mock())
            whenever(it.getChildAt(1)).thenReturn(mock())
        }
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(target)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(target, expectedResourceName)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(target, "", expectedResourceName)
    }

    @Test
    fun `onTap ignores invisible or gone views`(forge: Forge) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        val invalidTarget: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            visible = false,
            clickable = true,
            forge = forge
        )
        val validTarget: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(invalidTarget)
            whenever(it.getChildAt(1)).thenReturn(validTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(validTarget, expectedResourceName)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(validTarget, "", expectedResourceName)
    }

    @Test
    fun `onTap ignores not clickable targets`(forge: Forge) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        val invalidTarget: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = false,
            forge = forge
        )
        val validTarget: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(invalidTarget)
            whenever(it.getChildAt(1)).thenReturn(validTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(validTarget, expectedResourceName)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(validTarget, "", expectedResourceName)
    }

    @Test
    fun `onTap does nothing if not visible ViewGroup contains visible views`(forge: Forge) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        val validTarget: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.visibility).thenReturn(forge.anElementFrom(View.INVISIBLE, View.GONE))
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(validTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(validTarget, expectedResourceName)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `onTap does nothing if no children present and decor view not clickable`(
        forge: Forge
    ) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(0)
        }
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            GesturesListener.MSG_NO_TARGET_TAP
        )
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `onTap send Action for Compose View { target inside ComposeView } `(
        forge: Forge
    ) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        val composeView: ComposeView = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(composeView)
        }
        val targetName = forge.anAlphabeticalString()
        val x = mockEvent.x
        val y = mockEvent.y
        val mockComposeActionTrackingStrategy: ActionTrackingStrategy = mock {
            whenever(it.findTargetForTap(composeView, x, y))
                .thenReturn(ViewTarget(null, targetName))
        }
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger,
            composeActionTrackingStrategy = mockComposeActionTrackingStrategy
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyNoInteractions(mockInternalLogger)
        verify(rumMonitor.mockInstance).addAction(
            eq(RumActionType.TAP),
            eq(targetName),
            eq(emptyMap())
        )
    }

    @Test
    fun `onTap keeps decorView as target if visible and clickable`(forge: Forge) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        ) {
            whenever(it.childCount).thenReturn(0)
        }
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(mockDecorView, expectedResourceName)

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(mockDecorView, "", expectedResourceName)
    }

    @Test
    fun `onTap adds the target id hexa if NFE while requesting resource id`(forge: Forge) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        val targetId = forge.anInt()
        val validTarget: View = mockView(
            id = targetId,
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(validTarget)
        }
        whenever(mockResources.getResourceEntryName(validTarget.id)).thenThrow(
            Resources.NotFoundException(
                forge.anAlphabeticalString()
            )
        )
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(
            validTarget,
            "",
            "0x${targetId.toHexString()}"
        )
    }

    @Test
    fun `onTap adds the target id hexa when getResourceEntryName returns null`(forge: Forge) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        val targetId = forge.anInt()
        val validTarget: View = mockView(
            id = targetId,
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(validTarget)
        }
        whenever(mockResources.getResourceEntryName(validTarget.id)).thenReturn(null)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(
            validTarget,
            "",
            "0x${targetId.toHexString()}"
        )
    }

    @Test
    fun `will not send any span if decor view view reference is null`(forge: Forge) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference<Window>(null),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `applies the extra attributes from the attributes providers`(forge: Forge) {
        val mockEvent: MotionEvent = forge.getForgery()
        val targetId = forge.anInt()
        val validTarget: View = mockView(
            id = targetId,
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(validTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(validTarget, expectedResourceName)
        var expectedAttributes: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to validTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )
        val providers = Array<ViewAttributesProvider>(forge.anInt(min = 0, max = 10)) {
            mock {
                whenever(it.extractAttributes(eq(validTarget), any())).thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    val map = it.arguments[1] as MutableMap<String, Any?>
                    map[forge.aString()] = forge.aString()
                    expectedAttributes = map
                    null
                }
            }
        }

        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            providers,
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )
        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verify(rumMonitor.mockInstance).addAction(
            RumActionType.TAP,
            "",
            expectedAttributes
        )
    }

    @Test
    fun `M use class simple name as target class name W tapIntercepted { canonicalName is null }`(
        forge: Forge
    ) {
        val mockEvent: MotionEvent = forge.getForgery()
        val targetId = forge.anInt()

        // we will use a LocalViewClass to reproduce the behaviour when getCanonicalName function
        // can return a null object.
        class LocalViewClass(context: Context) : View(context)

        val validTarget: LocalViewClass = mockView(
            id = targetId,
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(validTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(validTarget, expectedResourceName)
        var expectedAttributes: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to validTarget.javaClass.simpleName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )

        val providers = Array<ViewAttributesProvider>(forge.anInt(min = 0, max = 10)) {
            mock {
                whenever(it.extractAttributes(eq(validTarget), any())).thenAnswer {
                    @Suppress("UNCHECKED_CAST")
                    val map = it.arguments[1] as MutableMap<String, Any?>
                    map[forge.aString()] = forge.aString()
                    expectedAttributes = map
                    null
                }
            }
        }

        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            providers,
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )
        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verify(rumMonitor.mockInstance).addAction(
            RumActionType.TAP,
            "",
            expectedAttributes
        )
    }

    @Test
    fun `M use the custom target name W tapIntercepted { custom target name provided }`(
        forge: Forge
    ) {
        val mockEvent: MotionEvent = forge.getForgery()
        val targetId = forge.anInt()
        val fakeCustomTargetName = forge.anAlphabeticalString()
        val validTarget: View = mockView(
            id = targetId,
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(validTarget)).thenReturn(fakeCustomTargetName)
        }
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(validTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(validTarget, expectedResourceName)
        val expectedAttributes: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to validTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )

        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            interactionPredicate = mockInteractionPredicate,
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verify(rumMonitor.mockInstance).addAction(
            RumActionType.TAP,
            fakeCustomTargetName,
            expectedAttributes
        )
    }

    @Test
    fun `M use empty string as target name W tapIntercepted { custom target name empty }`(
        forge: Forge
    ) {
        val mockEvent: MotionEvent = forge.getForgery()
        val targetId = forge.anInt()
        val validTarget: View = mockView(
            id = targetId,
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(validTarget)).thenReturn("")
        }
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(validTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(validTarget, expectedResourceName)
        val expectedAttributes: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to validTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )

        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            interactionPredicate = mockInteractionPredicate,
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verify(rumMonitor.mockInstance).addAction(
            RumActionType.TAP,
            "",
            expectedAttributes
        )
    }

    @Test
    fun `M find target with both strategies W tap`(forge: Forge) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val tapEvent: MotionEvent = forge.getForgery()
        val targetId = forge.anInt()
        val endUpEvent: MotionEvent = forge.getForgery()
        val scrollingTarget: ScrollableListView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(scrollingTarget)).thenReturn(null)
        }
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(scrollingTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(scrollingTarget, expectedResourceName)
        val mockAndroidActionTrackingStrategy = mock<AndroidActionTrackingStrategy>()
        val mockComposeActionTrackingStrategy = mock<ActionTrackingStrategy>()
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            interactionPredicate = mockInteractionPredicate,
            contextRef = WeakReference(mockAppContext),
            androidActionTrackingStrategy = mockAndroidActionTrackingStrategy,
            composeActionTrackingStrategy = mockComposeActionTrackingStrategy,
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        testedListener.onSingleTapUp(tapEvent)
        testedListener.onUp(endUpEvent)

        // Then
        verify(mockAndroidActionTrackingStrategy).findTargetForTap(
            mockDecorView,
            startDownEvent.x,
            startDownEvent.y
        )
        verify(mockComposeActionTrackingStrategy).findTargetForTap(
            mockDecorView,
            startDownEvent.x,
            startDownEvent.y
        )
    }

    // region Internal

    private fun verifyMonitorCalledWithUserAction(
        target: View,
        expectedTargetName: String,
        expectedResourceName: String
    ) {
        verify(rumMonitor.mockInstance).addAction(
            eq(RumActionType.TAP),
            eq(expectedTargetName),
            argThat {
                val targetClassName = target.javaClass.canonicalName
                this[RumAttributes.ACTION_TARGET_CLASS_NAME] == targetClassName &&
                    this[RumAttributes.ACTION_TARGET_RESOURCE_ID] == expectedResourceName
            }
        )
    }

    // endregion
}
