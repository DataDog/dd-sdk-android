/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.Button
import android.widget.ListAdapter
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ScrollingView
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.Node
import com.datadog.android.rum.tracking.ViewTarget
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.api.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class GesturesListenerScrollSwipeTest : AbstractGesturesListenerTest() {

    // region Tests

    @ParameterizedTest
    @ValueSource(
        strings = [
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        ]
    )
    fun `M send an scroll rum event if fling not detected`(
        expectedDirection: String,
        forge: Forge
    ) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection)

        val scrollingTarget: ScrollableView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = endUpEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(scrollingTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(scrollingTarget, expectedResourceName)
        val expectedStartAttributes = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )
        val expectedStopAttributes = expectedStartAttributes +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)

        // Then

        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance)
                .startAction(RumActionType.SCROLL, "", expectedStartAttributes)
            verify(rumMonitor.mockInstance)
                .stopAction(RumActionType.SCROLL, "", expectedStopAttributes)
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        ]
    )
    fun `M send a scroll rum event if target is a ListView`(
        expectedDirection: String,
        forge: Forge
    ) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection)

        val scrollingTarget: ScrollableListView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
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
        val expectedStartAttributes = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )
        val expectedStopAttributes = expectedStartAttributes +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance)
                .startAction(RumActionType.SCROLL, "", expectedStartAttributes)
            verify(rumMonitor.mockInstance)
                .stopAction(RumActionType.SCROLL, "", expectedStopAttributes)
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @ParameterizedTest
    @MethodSource("twoDirections")
    fun `M reset the scroll data between 2 consecutive gestures`(
        expectedDirection1: String,
        expectedDirection2: String,
        forge: Forge
    ) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]

        val scrollingTarget: ScrollableListView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
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
        val expectedStartAttributes1 = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )
        val expectedStartAttributes2 = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )
        val expectedStopAttributes1 = expectedStartAttributes1 +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection1)

        val expectedStopAttributes2 = expectedStartAttributes2 +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection2)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection1)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)

        testedListener.onDown(startDownEvent)
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection2)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance)
                .startAction(RumActionType.SCROLL, "", expectedStartAttributes1)
            verify(rumMonitor.mockInstance)
                .stopAction(RumActionType.SCROLL, "", expectedStopAttributes1)
            verify(rumMonitor.mockInstance)
                .startAction(RumActionType.SCROLL, "", expectedStartAttributes2)
            verify(rumMonitor.mockInstance)
                .stopAction(RumActionType.SCROLL, "", expectedStopAttributes2)
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        ]
    )
    fun `M send stopAction W gc occurs during scrolling`(
        expectedDirection1: String,
        forge: Forge
    ) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]

        val scrollingTarget: ScrollableListView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
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
        val expectedStartAttributes1 = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )

        val expectedStopAttributes1 = expectedStartAttributes1 +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection1)

        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection1)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        System.gc()
        testedListener.onUp(endUpEvent)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance)
                .startAction(RumActionType.SCROLL, "", expectedStartAttributes1)
            verify(rumMonitor.mockInstance)
                .stopAction(RumActionType.SCROLL, "", expectedStopAttributes1)
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        ]
    )
    fun `M send a tap rum event if target is a non scrollable`(
        expectedDirection: String,
        forge: Forge
    ) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents = forge.aList(size = listSize) {
            forge.getForgery(MotionEvent::class.java)
        }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        // ensure the last event is within the bounds of the target
        val endUpEvent = startDownEvent
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection)

        val nonScrollingTarget: Button = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            clickable = true,
            forge = forge
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(nonScrollingTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(nonScrollingTarget, expectedResourceName)
        val expectedStartAttributes = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to nonScrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)

        // Then
        verify(rumMonitor.mockInstance)
            .addAction(RumActionType.TAP, "", expectedStartAttributes)
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `will do nothing if there was no valid target `(forge: Forge) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        val scrollingTarget: ScrollableListView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = false,
            forge = forge
        )
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
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            {
                it == GesturesListener.MSG_NO_TARGET_ACTION
            },
            mode = times(intermediaryEvents.size + 2)
        )
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `will do nothing if target is in non-visible ViewGroup `(forge: Forge) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        // ensure the last event is within the bounds of the target
        val endUpEvent = startDownEvent
        val targetView = mockView<View>(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.visibility).thenReturn(forge.anElementFrom(View.INVISIBLE, View.GONE))
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(targetView)
        }
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        ]
    )
    fun `M add and stop scroll action W scroll on Compose View`(
        expectedDirection: String,
        forge: Forge
    ) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection)
        val composeView: ComposeView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(composeView)
        }
        val targetName = forge.anAlphabeticalString()
        val x = startDownEvent.x
        val y = startDownEvent.y
        val mockComposeActionTrackingStrategy: ActionTrackingStrategy = mock {
            whenever(it.findTargetForScroll(composeView, x, y))
                .thenReturn(ViewTarget(WeakReference(null), Node(targetName)))
        }
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger,
            composeActionTrackingStrategy = mockComposeActionTrackingStrategy
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)
        val expectedStartAttributes = mutableMapOf<String, Any>()
        val expectedStopAttributes = expectedStartAttributes +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection)
        // Then
        verifyNoInteractions(mockInternalLogger)
        verify(rumMonitor.mockInstance).startAction(
            eq(RumActionType.SCROLL),
            eq(targetName),
            eq(expectedStartAttributes)
        )
        verify(rumMonitor.mockInstance).stopAction(
            eq(RumActionType.SCROLL),
            eq(targetName),
            eq(expectedStopAttributes)
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        ]
    )
    fun `M send a swipe rum event if detected`(expectedDirection: String, forge: Forge) {
        val listSize = forge.anInt(1, 20)
        val startDownEvent: MotionEvent = forge.getForgery()
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection)
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val velocityX = forge.aFloat()
        val velocityY = forge.aFloat()
        val targetId = forge.anInt()
        val scrollingTarget: ScrollableView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
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
        val expectedStartAttributes = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )
        val expectedStopAttributes = expectedStartAttributes +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onFling(startDownEvent, endUpEvent, velocityX, velocityY)
        testedListener.onUp(endUpEvent)

        // Then

        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance)
                .startAction(RumActionType.SCROLL, "", expectedStartAttributes)
            verify(rumMonitor.mockInstance)
                .stopAction(RumActionType.SWIPE, "", expectedStopAttributes)
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        ]
    )
    fun `M use the class simple name as target name W scrollDetected { canonicalName is null }`(
        expectedDirection: String,
        forge: Forge
    ) {
        val listSize = forge.anInt(1, 20)
        val startDownEvent: MotionEvent = forge.getForgery()
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection)
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val velocityX = forge.aFloat()
        val velocityY = forge.aFloat()
        val targetId = forge.anInt()

        // we will use a LocalViewClass to reproduce the behaviour when getCanonicalName function
        // can return a null object.
        class LocalScrollableView : ScrollableListView()

        val scrollingTarget: LocalScrollableView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
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
        val expectedStartAttributes = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.simpleName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )
        val expectedStopAttributes = expectedStartAttributes +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onFling(startDownEvent, endUpEvent, velocityX, velocityY)
        testedListener.onUp(endUpEvent)

        // Then

        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance)
                .startAction(RumActionType.SCROLL, "", expectedStartAttributes)
            verify(rumMonitor.mockInstance)
                .stopAction(RumActionType.SWIPE, "", expectedStopAttributes)
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M use the custom target name W scrollDetected { custom target name provided }`(
        forge: Forge
    ) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        val scrollingTarget: ScrollableListView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
        val fakeCustomTargetName = forge.anAlphabeticalString()
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(scrollingTarget)).thenReturn(fakeCustomTargetName)
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
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            interactionPredicate = mockInteractionPredicate,
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startAction(
                eq(RumActionType.SCROLL),
                eq(fakeCustomTargetName),
                any()
            )
            verify(rumMonitor.mockInstance).stopAction(
                eq(RumActionType.SCROLL),
                eq(fakeCustomTargetName),
                any()
            )
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M use an empty target name W scrollDetected { custom target name is empty }`(
        forge: Forge
    ) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        val scrollingTarget: ScrollableListView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(scrollingTarget)).thenReturn("")
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
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            interactionPredicate = mockInteractionPredicate,
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startAction(
                eq(RumActionType.SCROLL),
                eq(""),
                any()
            )
            verify(rumMonitor.mockInstance).stopAction(
                eq(RumActionType.SCROLL),
                eq(""),
                any()
            )
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M use an empty target name W scrollDetected { custom target name is null }`(
        forge: Forge
    ) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
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
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            interactionPredicate = mockInteractionPredicate,
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onUp(endUpEvent)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startAction(
                eq(RumActionType.SCROLL),
                eq(""),
                any()
            )
            verify(rumMonitor.mockInstance).stopAction(
                eq(RumActionType.SCROLL),
                eq(""),
                any()
            )
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @ParameterizedTest
    @MethodSource("twoDirections")
    fun `M reset the swipe data between 2 consecutive gestures`(
        expectedDirection1: String,
        expectedDirection2: String,
        forge: Forge
    ) {
        val listSize = forge.anInt(1, 20)
        val startDownEvent: MotionEvent = forge.getForgery()
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]

        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val velocityX = forge.aFloat()
        val velocityY = forge.aFloat()
        val targetId = forge.anInt()
        val scrollingTarget: ScrollableView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
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
        val expectedStartAttributes1 = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )
        val expectedStartAttributes2 = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName
        )
        val expectedStopAttributes1 = expectedStartAttributes1 +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection1)
        val expectedStopAttributes2 = expectedStartAttributes2 +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection2)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )

        // When
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection1)
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onFling(startDownEvent, endUpEvent, velocityX, velocityY)
        testedListener.onUp(endUpEvent)

        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection2)
        testedListener.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            testedListener.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        testedListener.onFling(startDownEvent, endUpEvent, velocityX, velocityY)
        testedListener.onUp(endUpEvent)

        // Then

        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance)
                .startAction(RumActionType.SCROLL, "", expectedStartAttributes1)
            verify(rumMonitor.mockInstance)
                .stopAction(RumActionType.SWIPE, "", expectedStopAttributes1)
            verify(rumMonitor.mockInstance)
                .startAction(RumActionType.SCROLL, "", expectedStartAttributes2)
            verify(rumMonitor.mockInstance)
                .stopAction(RumActionType.SWIPE, "", expectedStopAttributes2)
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `on touchUp will do nothing if there was no scroll or swipe event detected`(forge: Forge) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val endUpEvent: MotionEvent = forge.getForgery()
        val targetId = forge.anInt()
        val scrollingTarget: ScrollableView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
        mockDecorView = mockDecorView<ViewGroup>(
            id = forge.anInt(),
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(scrollingTarget)
        }

        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger
        )
        testedListener.onUp(startDownEvent)
        testedListener.onDown(endUpEvent)

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M find target with both strategies W scroll`(forge: Forge) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val scrollEvent: MotionEvent = forge.getForgery()
        val distancesX = forge.aFloat()
        val distancesY = forge.aFloat()
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
        testedListener.onScroll(startDownEvent, scrollEvent, distancesX, distancesY)
        testedListener.onUp(endUpEvent)

        // Then
        verify(mockAndroidActionTrackingStrategy).findTargetForScroll(
            mockDecorView,
            startDownEvent.x,
            startDownEvent.y
        )
        verify(mockComposeActionTrackingStrategy).findTargetForScroll(
            mockDecorView,
            startDownEvent.x,
            startDownEvent.y
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        ]
    )
    fun `M add compose node attributes W send Scroll action`(
        expectedDirection: String,
        forge: Forge
    ) {
        val mockEvent: MotionEvent = forge.getForgery()
        val startDownEvent: MotionEvent = forge.getForgery()
        val scrollEvent: MotionEvent = forge.getForgery()
        val distancesX = forge.aFloat()
        val distancesY = forge.aFloat()
        val endUpEvent: MotionEvent = forge.getForgery()
        val targetId = forge.anInt()
        val fakeCustomTargetName = forge.anAlphabeticalString()
        val validTarget: View = mockView(
            id = targetId,
            forEvent = mockEvent,
            hitTest = true,
            forge = forge,
            clickable = true
        )
        val fakeAttributes = mapOf(
            RumAttributes.ACTION_TARGET_ROLE to forge.aString()
        )
        val mockInteractionPredicate: InteractionPredicate = mock {
            whenever(it.getTargetName(validTarget)).thenReturn(fakeCustomTargetName)
        }
        val mockComposeActionTrackingStrategy = mock<ActionTrackingStrategy>()
        val mockAndroidActionTrackingStrategy = mock<ActionTrackingStrategy>()
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
        val expectedStopAttributes = fakeAttributes +
            (RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection)
        testedListener = GesturesListener(
            rumMonitor.mockSdkCore,
            WeakReference(mockWindow),
            interactionPredicate = mockInteractionPredicate,
            contextRef = WeakReference(mockAppContext),
            internalLogger = mockInternalLogger,
            androidActionTrackingStrategy = mockAndroidActionTrackingStrategy,
            composeActionTrackingStrategy = mockComposeActionTrackingStrategy
        )
        stubStopMotionEvent(endUpEvent, startDownEvent, expectedDirection)
        whenever(
            mockAndroidActionTrackingStrategy.findTargetForScroll(
                mockDecorView,
                startDownEvent.x,
                startDownEvent.y
            )
        ).thenReturn(ViewTarget(viewRef = WeakReference<View?>(null)))

        whenever(
            mockComposeActionTrackingStrategy.findTargetForScroll(
                mockDecorView,
                startDownEvent.x,
                startDownEvent.y
            )
        ).thenReturn(ViewTarget(node = Node(fakeCustomTargetName, fakeAttributes)))

        // When
        testedListener.onDown(startDownEvent)
        testedListener.onScroll(startDownEvent, scrollEvent, distancesX, distancesY)
        testedListener.onUp(endUpEvent)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startAction(
                eq(RumActionType.SCROLL),
                eq(fakeCustomTargetName),
                eq(fakeAttributes)
            )
            verify(rumMonitor.mockInstance).stopAction(
                eq(RumActionType.SCROLL),
                eq(fakeCustomTargetName),
                eq(expectedStopAttributes)
            )
        }
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    // endregion

    // region internal

    internal class ScrollableView : View(mock()), ScrollingView {
        override fun computeVerticalScrollOffset(): Int {
            return 0
        }

        override fun computeVerticalScrollExtent(): Int {
            return 0
        }

        override fun computeVerticalScrollRange(): Int {
            return 0
        }

        override fun computeHorizontalScrollOffset(): Int {
            return 0
        }

        override fun computeHorizontalScrollRange(): Int {
            return 0
        }

        override fun computeHorizontalScrollExtent(): Int {
            return 0
        }
    }

    internal open class ScrollableListView : AbsListView(mock()) {
        override fun getAdapter(): ListAdapter {
            return mock()
        }

        override fun setSelection(position: Int) {
        }
    }

    private fun stubStopMotionEvent(
        stopEvent: MotionEvent,
        startEvent: MotionEvent,
        direction: String
    ) {
        val initialStartX = startEvent.x
        val initialStartY = startEvent.y
        when (direction) {
            GesturesListener.SCROLL_DIRECTION_UP -> {
                whenever(stopEvent.x).thenReturn(initialStartX)
                whenever(stopEvent.y).thenReturn((initialStartY - 2))
            }

            GesturesListener.SCROLL_DIRECTION_DOWN -> {
                whenever(stopEvent.x).thenReturn(initialStartX)
                whenever(stopEvent.y).thenReturn((initialStartY + 2))
            }

            GesturesListener.SCROLL_DIRECTION_RIGHT -> {
                whenever(stopEvent.x).thenReturn((initialStartX + 2))
                whenever(stopEvent.y).thenReturn(initialStartY)
            }

            GesturesListener.SCROLL_DIRECTION_LEFT -> {
                whenever(stopEvent.x).thenReturn((initialStartX - 2))
                whenever(stopEvent.y).thenReturn(initialStartY)
            }
        }
    }

    companion object {

        @Suppress("unused")
        @JvmStatic
        fun twoDirections(): Stream<Arguments> {
            val directions = listOf(
                GesturesListener.SCROLL_DIRECTION_DOWN,
                GesturesListener.SCROLL_DIRECTION_UP,
                GesturesListener.SCROLL_DIRECTION_LEFT,
                GesturesListener.SCROLL_DIRECTION_RIGHT
            )

            return directions.flatMap { first ->
                directions.map { second -> Arguments.of(first, second) }
            }.stream()
        }
    }

    // endregion
}
