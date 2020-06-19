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
import android.widget.ListAdapter
import androidx.core.view.ScrollingView
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.ref.WeakReference
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
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class GesturesListenerScrollSwipeTest : AbstractGesturesListenerTest() {

    @Mock
    lateinit var mockDatadogRumMonitor: DatadogRumMonitor

    // region Tests

    @BeforeEach
    override fun `set up`() {
        super.`set up`()
        GlobalRum.registerIfAbsent(mockDatadogRumMonitor)
    }

    @Test
    fun `it will send an scroll rum event if fling not detected`(forge: Forge) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        val expectedDirection = forge.anElementFrom(
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        )
        setupEventsForSwipeDirection(expectedDirection, startDownEvent, endUpEvent)

        val scrollingTarget: ScrollableView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
        decorView = mockView<ViewGroup>(
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
        val expectedAttributes: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName,
            RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection
        )
        underTest = GesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            underTest.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        underTest.onUp(endUpEvent)

        // then

        inOrder(mockDatadogRumMonitor) {
            verify(mockDatadogRumMonitor).startUserAction(RumActionType.CUSTOM, "", emptyMap())
            val argumentCaptor = argumentCaptor<Map<String, Any?>>()
            verify(mockDatadogRumMonitor).stopUserAction(
                eq(RumActionType.SCROLL),
                eq(targetName(scrollingTarget, expectedResourceName)),
                argumentCaptor.capture()
            )
            assertThat(argumentCaptor.firstValue).isEqualTo(expectedAttributes)
        }
        verifyNoMoreInteractions(mockDatadogRumMonitor)
    }

    @Test
    fun `it will send a scroll rum event if target is a ListView`(forge: Forge) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        val expectedDirection = forge.anElementFrom(
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        )
        setupEventsForSwipeDirection(expectedDirection, startDownEvent, endUpEvent)

        val scrollingTarget: ScrollableListView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
        decorView = mockView<ViewGroup>(
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
        val expectedAttributes: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName,
            RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection
        )
        underTest = GesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            underTest.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        underTest.onUp(endUpEvent)

        // then

        inOrder(mockDatadogRumMonitor) {
            verify(mockDatadogRumMonitor).startUserAction(RumActionType.CUSTOM, "", emptyMap())
            val argumentCaptor = argumentCaptor<Map<String, Any?>>()
            verify(mockDatadogRumMonitor).stopUserAction(
                eq(RumActionType.SCROLL),
                eq(targetName(scrollingTarget, expectedResourceName)),
                argumentCaptor.capture()
            )
            assertThat(argumentCaptor.firstValue).isEqualTo(expectedAttributes)
        }
        verifyNoMoreInteractions(mockDatadogRumMonitor)
    }

    @Test
    fun `it will reset the scroll data between 2 consecutive gestures`(forge: Forge) {
        val startDownEvent: MotionEvent = forge.getForgery()
        val listSize = forge.anInt(1, 20)
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val distancesX = forge.aList(listSize) { forge.aFloat() }
        val distancesY = forge.aList(listSize) { forge.aFloat() }
        val targetId = forge.anInt()
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        val expectedDirection1 = forge.anElementFrom(
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        )
        val expectedDirection2 = forge.anElementFrom(
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        )
        val scrollingTarget: ScrollableListView = mockView(
            id = targetId,
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        )
        decorView = mockView<ViewGroup>(
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
        val expectedAttributes1: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName,
            RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection1
        )
        val expectedAttributes2: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName,
            RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection2
        )
        underTest = GesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onDown(startDownEvent)
        setupEventsForSwipeDirection(expectedDirection1, startDownEvent, endUpEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            underTest.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        underTest.onUp(endUpEvent)

        underTest.onDown(startDownEvent)
        setupEventsForSwipeDirection(expectedDirection2, startDownEvent, endUpEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            underTest.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        underTest.onUp(endUpEvent)

        // then

        inOrder(mockDatadogRumMonitor) {
            verify(mockDatadogRumMonitor).startUserAction(RumActionType.CUSTOM, "", emptyMap())
            val argumentCaptor1 = argumentCaptor<Map<String, Any?>>()
            verify(mockDatadogRumMonitor).stopUserAction(
                eq(RumActionType.SCROLL),
                eq(targetName(scrollingTarget, expectedResourceName)),
                argumentCaptor1.capture()
            )
            assertThat(argumentCaptor1.firstValue).isEqualTo(expectedAttributes1)
            verify(mockDatadogRumMonitor).startUserAction(RumActionType.CUSTOM, "", emptyMap())
            val argumentCaptor2 = argumentCaptor<Map<String, Any?>>()
            verify(mockDatadogRumMonitor).stopUserAction(
                eq(RumActionType.SCROLL),
                eq(targetName(scrollingTarget, expectedResourceName)),
                argumentCaptor2.capture()
            )
            assertThat(argumentCaptor2.firstValue).isEqualTo(expectedAttributes2)
        }
        verifyNoMoreInteractions(mockDatadogRumMonitor)
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
        decorView = mockView<ViewGroup>(
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
        underTest = GesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            underTest.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        underTest.onUp(endUpEvent)

        // then
        verifyZeroInteractions(mockDatadogRumMonitor)
    }

    @Test
    fun `will do nothing if the registered monitor is not a DatadogRumMonitor`(forge: Forge) {
        `tear down`()
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
        decorView = mockView<ViewGroup>(
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
        underTest = GesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            underTest.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        underTest.onUp(endUpEvent)

        // then
        verifyZeroInteractions(mockDatadogRumMonitor)
    }

    @Test
    fun `it will send a swipe rum event if detected`(forge: Forge) {
        val listSize = forge.anInt(1, 20)
        val startDownEvent: MotionEvent = forge.getForgery()
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        val expectedDirection = forge.anElementFrom(
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        )
        setupEventsForSwipeDirection(expectedDirection, startDownEvent, endUpEvent)
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
        decorView = mockView<ViewGroup>(
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
        val expectedAttributes: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName,
            RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection
        )
        underTest = GesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            underTest.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        underTest.onFling(startDownEvent, endUpEvent, velocityX, velocityY)
        underTest.onUp(endUpEvent)

        // then

        inOrder(mockDatadogRumMonitor) {
            verify(mockDatadogRumMonitor).startUserAction(RumActionType.CUSTOM, "", emptyMap())
            val argumentCaptor = argumentCaptor<Map<String, Any?>>()
            verify(mockDatadogRumMonitor).stopUserAction(
                eq(RumActionType.SWIPE),
                eq(targetName(scrollingTarget, expectedResourceName)),
                argumentCaptor.capture()
            )
            assertThat(argumentCaptor.firstValue).isEqualTo(expectedAttributes)
        }
        verifyNoMoreInteractions(mockDatadogRumMonitor)
    }

    @Test
    fun `it will reset the swipe data between 2 consecutive gestures`(forge: Forge) {
        val listSize = forge.anInt(1, 20)
        val startDownEvent: MotionEvent = forge.getForgery()
        val intermediaryEvents =
            forge.aList(size = listSize) { forge.getForgery(MotionEvent::class.java) }
        val endUpEvent = intermediaryEvents[intermediaryEvents.size - 1]
        val expectedDirection1 = forge.anElementFrom(
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        )
        val expectedDirection2 = forge.anElementFrom(
            GesturesListener.SCROLL_DIRECTION_DOWN,
            GesturesListener.SCROLL_DIRECTION_UP,
            GesturesListener.SCROLL_DIRECTION_LEFT,
            GesturesListener.SCROLL_DIRECTION_RIGHT
        )
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
        decorView = mockView<ViewGroup>(
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
        val expectedAttributes1: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName,
            RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection1
        )
        val expectedAttributes2: MutableMap<String, Any?> = mutableMapOf(
            RumAttributes.ACTION_TARGET_CLASS_NAME to scrollingTarget.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to expectedResourceName,
            RumAttributes.ACTION_GESTURE_DIRECTION to expectedDirection2
        )
        underTest = GesturesListener(
            WeakReference(decorView)
        )

        // when
        setupEventsForSwipeDirection(expectedDirection1, startDownEvent, endUpEvent)
        underTest.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            underTest.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        underTest.onFling(startDownEvent, endUpEvent, velocityX, velocityY)
        underTest.onUp(endUpEvent)

        setupEventsForSwipeDirection(expectedDirection2, startDownEvent, endUpEvent)
        underTest.onDown(startDownEvent)
        intermediaryEvents.forEachIndexed { index, event ->
            underTest.onScroll(startDownEvent, event, distancesX[index], distancesY[index])
        }
        underTest.onFling(startDownEvent, endUpEvent, velocityX, velocityY)
        underTest.onUp(endUpEvent)

        // then

        inOrder(mockDatadogRumMonitor) {
            verify(mockDatadogRumMonitor).startUserAction(RumActionType.CUSTOM, "", emptyMap())
            val argumentCaptor1 = argumentCaptor<Map<String, Any?>>()
            verify(mockDatadogRumMonitor).stopUserAction(
                eq(RumActionType.SWIPE),
                eq(targetName(scrollingTarget, expectedResourceName)),
                argumentCaptor1.capture()
            )
            assertThat(argumentCaptor1.firstValue).isEqualTo(expectedAttributes1)
            verify(mockDatadogRumMonitor).startUserAction(RumActionType.CUSTOM, "", emptyMap())
            val argumentCaptor2 = argumentCaptor<Map<String, Any?>>()
            verify(mockDatadogRumMonitor).stopUserAction(
                eq(RumActionType.SWIPE),
                eq(targetName(scrollingTarget, expectedResourceName)),
                argumentCaptor2.capture()
            )
            assertThat(argumentCaptor2.firstValue).isEqualTo(expectedAttributes2)
        }
        verifyNoMoreInteractions(mockDatadogRumMonitor)
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
        decorView = mockView<ViewGroup>(
            id = forge.anInt(),
            forEvent = startDownEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(scrollingTarget)
        }

        underTest = GesturesListener(
            WeakReference(decorView)
        )
        underTest.onUp(startDownEvent)
        underTest.onDown(endUpEvent)

        verifyZeroInteractions(mockDatadogRumMonitor)
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

    internal class ScrollableListView : AbsListView(mock()) {
        override fun getAdapter(): ListAdapter {
            return mock()
        }

        override fun setSelection(position: Int) {
        }
    }

    private fun setupEventsForSwipeDirection(
        direction: String,
        startEvent: MotionEvent,
        stopEvent: MotionEvent
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
            GesturesListener.SCROLL_DIRECTION_LEFT -> {
                whenever(stopEvent.x).thenReturn((initialStartX + 2))
                whenever(stopEvent.y).thenReturn(initialStartY)
            }
            GesturesListener.SCROLL_DIRECTION_RIGHT -> {
                whenever(stopEvent.x).thenReturn((initialStartX - 2))
                whenever(stopEvent.y).thenReturn(initialStartY)
            }
        }
    }

    // endregion
}
