/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.res.Resources
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockDevLogHandler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.ref.WeakReference
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class GesturesListenerTapTest : AbstractGesturesListenerTest() {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    lateinit var mockDevLogHandler: LogHandler

    @BeforeEach
    override fun `set up`() {
        super.`set up`()
        mockDevLogHandler = mockDevLogHandler()
        GlobalRum.registerIfAbsent(mockRumMonitor)
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
            WeakReference(mockWindow)
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(target, expectedResourceName)
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
            WeakReference(mockWindow)
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(target, expectedResourceName)
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
            WeakReference(mockWindow)
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(validTarget, expectedResourceName)
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
            WeakReference(mockWindow)
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(validTarget, expectedResourceName)
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
            WeakReference(mockWindow)
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verify(mockDevLogHandler)
            .handleLog(
                Log.INFO,
                GesturesListener.MSG_NO_TARGET_TAP
            )
        verifyZeroInteractions(mockRumMonitor)
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
            WeakReference(mockWindow)
        )
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(mockDecorView, expectedResourceName)

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(mockDecorView, expectedResourceName)
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
            WeakReference(mockWindow)
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(validTarget, "0x${targetId.toString(16)}")
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
            WeakReference(mockWindow)
        )

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyMonitorCalledWithUserAction(validTarget, "0x${targetId.toString(16)}")
    }

    @Test
    fun `will not send any span if decor view view reference is null`(forge: Forge) {
        // Given
        val mockEvent: MotionEvent = forge.getForgery()
        testedListener = GesturesListener(WeakReference<Window>(null))

        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verifyZeroInteractions(mockRumMonitor)
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
                    val map = it.arguments[1] as MutableMap<String, Any?>
                    map[forge.aString()] = forge.aString()
                    expectedAttributes = map
                    null
                }
            }
        }

        testedListener = GesturesListener(
            WeakReference(mockWindow),
            providers
        )
        // When
        testedListener.onSingleTapUp(mockEvent)

        // Then
        verify(mockRumMonitor).addUserAction(
            RumActionType.TAP,
            targetName(validTarget, expectedResourceName),
            expectedAttributes
        )
    }

    // region Internal

    private fun verifyMonitorCalledWithUserAction(target: View, expectedResourceName: String) {
        verify(mockRumMonitor).addUserAction(
            eq(RumActionType.TAP),
            argThat { startsWith("${target.javaClass.simpleName}(") },
            argThat {
                val targetClassName = target.javaClass.canonicalName
                this[RumAttributes.ACTION_TARGET_CLASS_NAME] == targetClassName &&
                    this[RumAttributes.ACTION_TARGET_RESOURCE_ID] == expectedResourceName
            }
        )
    }

    // endregion
}
