/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.app.Application
import android.content.res.Resources
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import com.datadog.android.Datadog
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.ref.WeakReference
import kotlin.math.abs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
internal abstract class AbstractGesturesListenerTest {

    lateinit var testedListener: GesturesListener

    lateinit var mockDecorView: View

    @Mock
    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockWindow: Window

    // region Tests

    @BeforeEach
    open fun `set up`() {
        Datadog.setVerbosity(Log.VERBOSE)
        whenever(mockAppContext.resources).thenReturn(mockResources)
        CoreFeature.contextRef = WeakReference(mockAppContext)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.setVerbosity(Integer.MAX_VALUE)
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
        CoreFeature.contextRef = WeakReference(null)
    }

    // endregion

    // region Internal

    protected inline fun <reified T : View> mockDecorView(
        id: Int,
        forEvent: MotionEvent,
        hitTest: Boolean,
        clickable: Boolean = false,
        visible: Boolean = true,
        forge: Forge,
        applyOthers: (T) -> Unit = {}
    ): T {
        val decorView = mockView<T>(id, forEvent, hitTest, clickable, visible, forge, applyOthers)
        whenever(mockWindow.decorView) doReturn decorView
        return decorView
    }

    protected inline fun <reified T : View> mockView(
        id: Int,
        forEvent: MotionEvent,
        hitTest: Boolean,
        clickable: Boolean = false,
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
        val mockView: T = mock {
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

        return mockView
    }

    protected fun mockResourcesForTarget(target: View, expectedResourceName: String) {
        whenever(mockResources.getResourceEntryName(target.id)).thenReturn(expectedResourceName)
    }

    // endregion
}
