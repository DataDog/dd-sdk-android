package com.datadog.android.rum.gestures

import android.content.res.Resources
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
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
internal class DatadogGesturesListenerTest {

    lateinit var underTest: DatadogGesturesListener

    @Mock
    lateinit var mockRumTracer: AndroidTracer

    @Mock
    lateinit var mockSpanBuilder: DDTracer.DDSpanBuilder

    @Mock
    lateinit var mockSpan: DDSpan

    lateinit var decorView: View

    @BeforeEach
    fun `set up`() {
        mockRumTracer.apply {
            whenever(buildSpan(DatadogGesturesListener.UI_TAP_ACTION_EVENT))
                .thenReturn(mockSpanBuilder)
        }
        mockSpanBuilder.apply {
            whenever(withTag(any(), any<String>())).thenReturn(this)
            whenever(start()).thenReturn(mockSpan)
        }
    }

    // region Tests
    @Test
    fun `onTap creates the span when target deep in the View Hierarchy`(forge: Forge) {
        // given
        val mockEvent = mockEvent(forge.aFloat(), forge.aFloat())
        val container1: ViewGroup = mock(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false
        )
        val target: View = mock(id = forge.anInt(), forEvent = mockEvent, hitTest = true)
        val notClickableInvalidTarget: View = mock(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = false
        )
        val notVisibleInvalidTarget: View = mock(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            visible = false
        )
        val container2: ViewGroup = mock(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true
        ) {
            whenever(it.childCount).thenReturn(3)
            whenever(it.getChildAt(0)).thenReturn(notClickableInvalidTarget)
            whenever(it.getChildAt(1)).thenReturn(notVisibleInvalidTarget)
            whenever(it.getChildAt(2)).thenReturn(target)
        }
        decorView = mock<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(container1)
            whenever(it.getChildAt(1)).thenReturn(container2)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(target, expectedResourceName)
        underTest = DatadogGesturesListener(
            mockRumTracer,
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verify(mockRumTracer).buildSpan(DatadogGesturesListener.UI_TAP_ACTION_EVENT)
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_CLASS_NAME,
            target.javaClass.canonicalName
        )
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_RESOURCE_ID,
            expectedResourceName
        )
        verify(mockSpan).finish(DatadogGesturesListener.DEFAULT_EVENT_DURATION)
    }

    @Test
    fun `onTap creates the span if target is ViewGroup with no children`(forge: Forge) {
        // given
        val mockEvent = mockEvent(forge.aFloat(), forge.aFloat())
        val target: ViewGroup = mock(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true
        ) {
            whenever(it.childCount).thenReturn(0)
        }
        val container2: ViewGroup = mock(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(mock())
            whenever(it.getChildAt(1)).thenReturn(mock())
        }
        decorView = mock<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(target)
            whenever(it.getChildAt(1)).thenReturn(container2)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        val mockResources = mock<Resources> {
            whenever(it.getResourceEntryName(target.id)).thenReturn(expectedResourceName)
        }
        whenever(target.resources).thenReturn(mockResources)
        underTest = DatadogGesturesListener(
            mockRumTracer,
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verify(mockRumTracer).buildSpan(DatadogGesturesListener.UI_TAP_ACTION_EVENT)
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_CLASS_NAME,
            target.javaClass.canonicalName
        )
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_RESOURCE_ID,
            expectedResourceName
        )
        verify(mockSpan).finish(DatadogGesturesListener.DEFAULT_EVENT_DURATION)
    }

    @Test
    fun `onTap creates span for valid visible target`(forge: Forge) {
        // given
        val mockEvent = mockEvent(forge.aFloat(), forge.aFloat())
        val invalidTarget: View = mock(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            visible = false
        )
        val validTarget: View = mock(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true
        )
        decorView = mock<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(invalidTarget)
            whenever(it.getChildAt(1)).thenReturn(validTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(validTarget, expectedResourceName)
        underTest = DatadogGesturesListener(
            mockRumTracer,
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verify(mockRumTracer).buildSpan(DatadogGesturesListener.UI_TAP_ACTION_EVENT)
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_CLASS_NAME,
            validTarget.javaClass.canonicalName
        )
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_RESOURCE_ID,
            expectedResourceName
        )
        verify(mockSpan).finish(DatadogGesturesListener.DEFAULT_EVENT_DURATION)
    }

    @Test
    fun `onTap creates span for valid clickable target`(forge: Forge) {
        // given
        val mockEvent = mockEvent(forge.aFloat(), forge.aFloat())
        val invalidTarget: View = mock(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = false
        )
        val validTarget: View = mock(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true
        )
        decorView = mock<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(invalidTarget)
            whenever(it.getChildAt(1)).thenReturn(validTarget)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(validTarget, expectedResourceName)
        underTest = DatadogGesturesListener(
            mockRumTracer,
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verify(mockRumTracer).buildSpan(DatadogGesturesListener.UI_TAP_ACTION_EVENT)
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_CLASS_NAME,
            validTarget.javaClass.canonicalName
        )
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_RESOURCE_ID,
            expectedResourceName
        )
        verify(mockSpan).finish(DatadogGesturesListener.DEFAULT_EVENT_DURATION)
    }

    @Test
    fun `onTap creates span with decorView as target if no children present`(forge: Forge) {
        // given
        val mockEvent = mockEvent(forge.aFloat(), forge.aFloat())
        decorView = mock<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false
        ) {
            whenever(it.childCount).thenReturn(0)
        }
        val expectedResourceName = forge.anAlphabeticalString()
        mockResourcesForTarget(decorView, expectedResourceName)
        underTest = DatadogGesturesListener(
            mockRumTracer,
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verify(mockRumTracer).buildSpan(DatadogGesturesListener.UI_TAP_ACTION_EVENT)
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_CLASS_NAME,
            decorView.javaClass.canonicalName
        )
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_RESOURCE_ID,
            expectedResourceName
        )
        verify(mockSpan).finish(DatadogGesturesListener.DEFAULT_EVENT_DURATION)
    }

    @Test
    fun `onTap does not add the resource name if any exception`(forge: Forge) {
        // given
        val mockEvent = mockEvent(forge.aFloat(), forge.aFloat())
        decorView = mock<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false
        ) {
            whenever(it.childCount).thenReturn(0)
        }
        val mockResources = mock<Resources> {
            whenever(it.getResourceEntryName(decorView.id))
                .thenThrow(Resources.NotFoundException(forge.anAlphabeticalString()))
        }
        whenever(decorView.resources).thenReturn(mockResources)
        underTest = DatadogGesturesListener(
            mockRumTracer,
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verify(mockRumTracer).buildSpan(DatadogGesturesListener.UI_TAP_ACTION_EVENT)
        verify(mockSpanBuilder).withTag(
            DatadogGesturesListener.TAG_TARGET_CLASS_NAME,
            decorView.javaClass.canonicalName
        )
        verify(mockSpanBuilder, never()).withTag(
            eq(DatadogGesturesListener.TAG_TARGET_RESOURCE_ID),
            any<String>()
        )
        verify(mockSpan).finish(DatadogGesturesListener.DEFAULT_EVENT_DURATION)
    }

    // endregion

    // region Internal

    private inline fun <reified T : View> mock(
        id: Int,
        forEvent: MotionEvent,
        hitTest: Boolean,
        clickable: Boolean = true,
        visible: Boolean = true,
        applyOthers: (T) -> Unit = {}
    ): T {

        val mockedView: T = mock {
            whenever(it.id).thenReturn(id)
            whenever(it.isClickable).thenReturn(clickable)
            whenever(it.visibility).thenReturn(if (visible) View.VISIBLE else View.GONE)
            val rect = mock<Rect>()
            whenever(it.clipBounds).thenReturn(rect)
            whenever(rect.contains(forEvent.x.toInt(), forEvent.y.toInt())).thenReturn(hitTest)
            applyOthers(this.mock)
        }

        return mockedView
    }

    private fun mockEvent(x: Float, y: Float): MotionEvent {
        return mock {
            whenever(it.x).thenReturn(x)
            whenever(it.y).thenReturn(y)
        }
    }

    private fun mockResourcesForTarget(target: View, expectedResourceName: String) {
        val mockResources = mock<Resources> {
            whenever(it.getResourceEntryName(target.id)).thenReturn(expectedResourceName)
        }
        whenever(target.resources).thenReturn(mockResources)
    }

    // endregion
}
