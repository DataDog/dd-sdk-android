package com.datadog.android.rum.gestures

import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.datadog.android.tracing.Tracer
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
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
import kotlin.math.abs
import kotlin.random.Random
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
    lateinit var mockRumTracer: Tracer
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
        val mockEvent = mockMotionEvent(forge)
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
        val mockEvent = mockMotionEvent(forge)
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
        val mockEvent = mockMotionEvent(forge)
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
        val mockEvent = mockMotionEvent(forge)
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
        val mockEvent = mockMotionEvent(forge)
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
        val mockEvent = mockMotionEvent(forge)
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

    private fun mockMotionEvent(forge: Forge): MotionEvent {
        return mock {
            whenever(it.x).thenReturn(forge.aFloat(min = 0f, max = XY_MAX_VALUE))
            whenever(it.y).thenReturn(forge.aFloat(min = 0f, max = XY_MAX_VALUE))
        }
    }

    private inline fun <reified T : View> mock(
        id: Int,
        forEvent: MotionEvent,
        hitTest: Boolean,
        clickable: Boolean = true,
        visible: Boolean = true,
        applyOthers: (T) -> Unit = {}
    ): T {

        val random = Random(System.currentTimeMillis())

        val failHitTestBecauseOfXY = random.nextBits(1) == 1
        val failHitTestBecauseOfWidthHeight = !failHitTestBecauseOfXY

        val locationOnScreenArray = IntArray(2)
        if (!hitTest && failHitTestBecauseOfXY) {
            locationOnScreenArray[0] = (forEvent.x).toInt() + random.nextInt(10)
            locationOnScreenArray[1] = (forEvent.y).toInt() + random.nextInt(10)
        } else {
            locationOnScreenArray[0] = (forEvent.x).toInt() - random.nextInt(10)
            locationOnScreenArray[1] = (forEvent.y).toInt() - random.nextInt(10)
        }
        val mockedView: T = mock {
            whenever(it.id).thenReturn(id)
            whenever(it.isClickable).thenReturn(clickable)
            whenever(it.visibility).thenReturn(if (visible) View.VISIBLE else View.GONE)

            whenever(it.getLocationOnScreen(any())).doAnswer {
                val array = it.arguments[0] as IntArray
                array[0] = locationOnScreenArray[0]
                array[1] = locationOnScreenArray[1]
                null
            }

            val diffPosX = abs((abs(forEvent.x) - abs(locationOnScreenArray[0]))).toInt()
            val diffPosY = abs((abs(forEvent.y) - abs(locationOnScreenArray[1]))).toInt()
            if (!hitTest && failHitTestBecauseOfWidthHeight) {
                whenever(it.width).thenReturn(diffPosX - random.nextInt(10))
                whenever(it.height).thenReturn(diffPosY - random.nextInt(10))
            } else {
                whenever(it.width).thenReturn(diffPosX + random.nextInt(10))
                whenever(it.height).thenReturn(diffPosY + random.nextInt(10))
            }

            applyOthers(this.mock)
        }

        return mockedView
    }

    private fun mockResourcesForTarget(target: View, expectedResourceName: String) {
        val mockResources = mock<Resources> {
            whenever(it.getResourceEntryName(target.id)).thenReturn(expectedResourceName)
        }
        whenever(target.resources).thenReturn(mockResources)
    }

    // endregion

    companion object {
        const val XY_MAX_VALUE = 1000f
    }
}
