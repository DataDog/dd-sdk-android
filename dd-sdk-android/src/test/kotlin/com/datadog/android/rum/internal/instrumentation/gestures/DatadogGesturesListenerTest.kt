package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.res.Resources
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.datadog.android.Datadog
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.monitor.NoOpRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockDevLogHandler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.ref.WeakReference
import kotlin.math.abs
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
    lateinit var mockRumMonitor: RumMonitor

    lateinit var mockDevLogHandler: LogHandler

    lateinit var decorView: View

    @BeforeEach
    fun `set up`() {
        mockDevLogHandler = mockDevLogHandler()
        Datadog.setVerbosity(Log.VERBOSE)
        GlobalRum.registerIfAbsent(mockRumMonitor)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.setVerbosity(Integer.MAX_VALUE)
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
    }

    // region Tests

    @Test
    fun `onTap dispatches an UserAction when target deep in the View Hierarchy`(forge: Forge) {
        // given
        val mockEvent = mockMotionEvent(forge)
        val container1: ViewGroup = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        )
        val target: View = mockView(
            id = forge.anInt(), forEvent = mockEvent, hitTest = true, forge = forge
        )
        val notClickableInvalidTarget: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = false,
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
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(3)
            whenever(it.getChildAt(0)).thenReturn(notClickableInvalidTarget)
            whenever(it.getChildAt(1)).thenReturn(notVisibleInvalidTarget)
            whenever(it.getChildAt(2)).thenReturn(target)
        }
        decorView = mockView<ViewGroup>(
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
        underTest = DatadogGesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verifyUserAction(target, expectedResourceName)
    }

    @Test
    fun `onTap dispatches an UserAction if target is ViewGroup with no children`(forge: Forge) {
        // given
        val mockEvent = mockMotionEvent(forge)
        val target: ViewGroup = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(0)
        }
        val container2: ViewGroup = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(2)
            whenever(it.getChildAt(0)).thenReturn(mock())
            whenever(it.getChildAt(1)).thenReturn(mock())
        }
        decorView = mockView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
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
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verifyUserAction(target, expectedResourceName)
    }

    @Test
    fun `onTap ignores invisible or gone views`(forge: Forge) {
        // given
        val mockEvent = mockMotionEvent(forge)
        val invalidTarget: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            visible = false,
            forge = forge
        )
        val validTarget: View = mockView(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        )
        decorView = mockView<ViewGroup>(
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
        underTest = DatadogGesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verifyUserAction(validTarget, expectedResourceName)
    }

    @Test
    fun `onTap ignores not clickable targets`(forge: Forge) {
        // given
        val mockEvent = mockMotionEvent(forge)
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
            forge = forge
        )
        decorView = mockView<ViewGroup>(
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
        underTest = DatadogGesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verifyUserAction(validTarget, expectedResourceName)
    }

    @Test
    fun `onTap does nothing if no children present and decor view not clickable`(
        forge: Forge
    ) {
        // given
        val mockDevLogHandler = mockDevLogHandler()
        val mockEvent = mockMotionEvent(forge)
        decorView = mockView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            clickable = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(0)
        }
        underTest = DatadogGesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verify(mockDevLogHandler)
            .handleLog(
                Log.INFO,
                DatadogGesturesListener.MSG_NO_TARGET
            )
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `onTap keeps decorView as target if visible and clickable`(forge: Forge) {
        // given
        val mockEvent = mockMotionEvent(forge)
        decorView = mockView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(0)
        }
        underTest = DatadogGesturesListener(
            WeakReference(decorView)
        )
        val expectedResourceName = forge.anAlphabeticalString()
        val mockResources = mock<Resources> {
            whenever(it.getResourceEntryName(decorView.id))
                .thenReturn(expectedResourceName)
        }
        whenever(decorView.resources).thenReturn(mockResources)

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verifyUserAction(decorView, expectedResourceName)
    }

    @Test
    fun `onTap adds the target id hexa if NFE while requesting resource id`(forge: Forge) {
        // given
        val mockEvent = mockMotionEvent(forge)
        val targetId = forge.anInt()
        val validTarget: View = mockView(
            id = targetId,
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        )
        decorView = mockView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(validTarget)
        }
        val mockResources = mock<Resources> {
            whenever(it.getResourceEntryName(validTarget.id))
                .thenThrow(Resources.NotFoundException(forge.anAlphabeticalString()))
        }
        whenever(validTarget.resources).thenReturn(mockResources)
        underTest = DatadogGesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verifyUserAction(validTarget, "0x${targetId.toString(16)}")
    }

    @Test
    fun `onTap adds the target id hexa when getResourceEntryName returns null`(forge: Forge) {
        // given
        val mockEvent = mockMotionEvent(forge)
        val targetId = forge.anInt()
        val validTarget: View = mockView(
            id = targetId,
            forEvent = mockEvent,
            hitTest = true,
            forge = forge
        )
        decorView = mockView<ViewGroup>(
            id = forge.anInt(),
            forEvent = mockEvent,
            hitTest = false,
            forge = forge
        ) {
            whenever(it.childCount).thenReturn(1)
            whenever(it.getChildAt(0)).thenReturn(validTarget)
        }
        val mockResources = mock<Resources> {
            whenever(it.getResourceEntryName(validTarget.id))
                .thenReturn(null)
        }
        whenever(validTarget.resources).thenReturn(mockResources)
        underTest = DatadogGesturesListener(
            WeakReference(decorView)
        )

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verifyUserAction(validTarget, "0x${targetId.toString(16)}")
    }

    @Test
    fun `will not send any span if decor view view reference is null`(forge: Forge) {
        // given
        val mockEvent = mockMotionEvent(forge)
        underTest = DatadogGesturesListener(WeakReference<View>(null))

        // when
        underTest.onSingleTapUp(mockEvent)

        // then
        verifyZeroInteractions(mockRumMonitor)
    }

    // endregion

    // region Internal

    private fun mockMotionEvent(forge: Forge): MotionEvent {
        return mock {
            whenever(it.x).thenReturn(forge.aFloat(min = 0f, max = XY_MAX_VALUE))
            whenever(it.y).thenReturn(forge.aFloat(min = 0f, max = XY_MAX_VALUE))
        }
    }

    private inline fun <reified T : View> mockView(
        id: Int,
        forEvent: MotionEvent,
        hitTest: Boolean,
        clickable: Boolean = true,
        visible: Boolean = true,
        forge: Forge,
        applyOthers: (T) -> Unit = {}
    ): T {

        val failHitTestBecauseOfXY = forge.aBool()
        val failHitTestBecauseOfWidthHeight = !failHitTestBecauseOfXY
        val locationOnScreenArray = IntArray(2)
        if (!hitTest && failHitTestBecauseOfXY) {
            locationOnScreenArray[0] = (forEvent.x).toInt() + forge.anInt(min = 1, max = 10)
            locationOnScreenArray[1] = (forEvent.y).toInt() + forge.anInt(min = 1, max = 10)
        } else {
            locationOnScreenArray[0] = (forEvent.x).toInt() - forge.anInt(min = 1, max = 10)
            locationOnScreenArray[1] = (forEvent.y).toInt() - forge.anInt(min = 1, max = 10)
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

            val diffPosX = abs(forEvent.x - locationOnScreenArray[0]).toInt()
            val diffPosY = abs(forEvent.y - locationOnScreenArray[1]).toInt()
            if (!hitTest && failHitTestBecauseOfWidthHeight) {
                whenever(it.width).thenReturn(diffPosX - forge.anInt(min = 1, max = 10))
                whenever(it.height).thenReturn(diffPosY - forge.anInt(min = 1, max = 10))
            } else {
                whenever(it.width).thenReturn(diffPosX + forge.anInt(min = 1, max = 10))
                whenever(it.height).thenReturn(diffPosY + forge.anInt(min = 1, max = 10))
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

    private fun verifyUserAction(target: View, expectedResourceName: String) {
        verify(mockRumMonitor).addUserAction(
            eq(DatadogGesturesListener.UI_TAP_ACTION_EVENT),
            argThat {
                val targetClassName = target.javaClass.canonicalName
                this[DatadogGesturesListener.TAG_TARGET_CLASS_NAME] == targetClassName &&
                    this[DatadogGesturesListener.TAG_TARGET_RESOURCE_ID] == expectedResourceName
            })
    }

    // endregion

    companion object {
        const val XY_MAX_VALUE = 1000f
    }
}
