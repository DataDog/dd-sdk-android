/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.navigation.ActivityNavigator
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.R
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.internal.domain.model.ViewEvent
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.tracking.ViewLoadingTimer
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class NavigationViewTrackingStrategyTest {

    lateinit var testedStrategy: NavigationViewTrackingStrategy

    @Mock
    lateinit var mockActivity: FragmentActivity

    @Mock
    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockNotAppContext: Context

    @Mock
    lateinit var mockNavView: View

    @Mock
    lateinit var mockNavController: NavController

    @Mock
    lateinit var mockFragmentManager: FragmentManager

    lateinit var mockNavDestination: NavDestination

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Mock
    lateinit var mockViewLoadingTimer: ViewLoadingTimer

    @Mock
    lateinit var mockFragment: Fragment

    @IntForgery
    var fakeNavViewId: Int = 0

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeDestinationName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockActivity.supportFragmentManager).thenReturn(mockFragmentManager)
        doReturn(mockNavView).whenever(mockActivity).findViewById<View>(fakeNavViewId)
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn mockNavController
        mockNavDestination = getMockNavDestination(forge, fakeDestinationName)

        GlobalRum.registerIfAbsent(mockRumMonitor)

        testedStrategy = NavigationViewTrackingStrategy(fakeNavViewId, true)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
    }

    // region ActivityLifecycleTrackingStrategy

    @Test
    fun `when register it will register as lifecycle callback`() {
        testedStrategy.register(mockAppContext)

        verify(mockAppContext).registerActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when unregister it will remove itself  as lifecycle callback`() {
        testedStrategy.unregister(mockAppContext)

        verify(mockAppContext).unregisterActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when register called with non application context will do nothing`() {
        testedStrategy.register(mockNotAppContext)

        verifyZeroInteractions(mockNotAppContext)
    }

    @Test
    fun `when unregister called with non application context will do nothing`() {
        testedStrategy.unregister(mockNotAppContext)

        verifyZeroInteractions(mockNotAppContext)
    }

    // endregion

    // region NavigationViewTrackingStrategy

    @Test
    fun `registers navigation listener onActivityStarted`() {
        testedStrategy.onActivityStarted(mockActivity)

        verify(mockNavController).addOnDestinationChangedListener(testedStrategy)
    }

    @Test
    fun `registers fragment lifecycle callback onActivityStarted`() {
        testedStrategy.onActivityStarted(mockActivity)

        verify(mockFragmentManager).registerFragmentLifecycleCallbacks(
            any<NavigationViewTrackingStrategy.NavControllerFragmentLifecycleCallbacks>(),
            eq(true)
        )
    }

    @Test
    fun `unregisters navigation listener onActivityStopped`() {
        testedStrategy.onActivityStopped(mockActivity)

        verify(mockNavController).removeOnDestinationChangedListener(testedStrategy)
    }

    @Test
    fun `unregisters fragment lifecycle callback onActivityStopped`() {
        // given
        testedStrategy.onActivityStarted(mockActivity)

        // when
        testedStrategy.onActivityStopped(mockActivity)

        // then
        val argumentCaptor =
            argumentCaptor<NavigationViewTrackingStrategy.NavControllerFragmentLifecycleCallbacks>()
        verify(mockFragmentManager)
            .registerFragmentLifecycleCallbacks(argumentCaptor.capture(), eq(true))
        verify(mockFragmentManager).unregisterFragmentLifecycleCallbacks(argumentCaptor.firstValue)
    }

    @Test
    fun `does nothing onActivityStarted when no NavHost present`() {
        testedStrategy.onActivityStarted(mock())

        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `does nothing onActivityStopped when no NavHost present`() {
        testedStrategy.onActivityStopped(mock())

        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `does nothing onActivityPaused when no NavHost present`() {
        testedStrategy.onActivityPaused(mock())

        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `does nothing onActivityStarted when no NavController present`() {
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null

        testedStrategy.onActivityStarted(mockActivity)

        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `does nothing onActivityStopped when no NavController present`() {
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null

        testedStrategy.onActivityStopped(mockActivity)

        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `does nothing onActivityPaused when no NavController present`() {
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null

        testedStrategy.onActivityPaused(mockActivity)

        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `starts view onDestinationChanged`() {
        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, null)

        verify(mockRumMonitor).startView(mockNavDestination, fakeDestinationName, emptyMap())
    }

    @Test
    fun `starts view onDestinationChanged with arguments`(
        forge: Forge
    ) {
        val arguments = Bundle()
        val expectedAttrs = mutableMapOf<String, Any?>()
        for (i in 0..10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
            expectedAttrs["view.arguments.$key"] = value
        }

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        verify(mockRumMonitor).startView(mockNavDestination, fakeDestinationName, expectedAttrs)
    }

    @Test
    fun `starts view onDestinationChanged with arguments un-tracked`(
        forge: Forge
    ) {
        testedStrategy = NavigationViewTrackingStrategy(fakeNavViewId, false)

        val arguments = Bundle()
        for (i in 0..10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
        }

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        verify(mockRumMonitor).startView(mockNavDestination, fakeDestinationName, emptyMap())
    }

    @Test
    fun `start new view onDestinationChanged`(
        forge: Forge,
        @StringForgery(StringForgeryType.ALPHABETICAL) newDestinationName: String
    ) {
        val newDestination = getMockNavDestination(forge, newDestinationName)
        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, null)
        whenever(mockNavController.currentDestination) doReturn mockNavDestination
        testedStrategy.onDestinationChanged(mockNavController, newDestination, null)

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startView(mockNavDestination, fakeDestinationName, emptyMap())
            verify(mockRumMonitor).startView(newDestination, newDestinationName, emptyMap())
        }
    }

    @Test
    fun `stops current view onActivityPaused`() {
        whenever(mockNavController.currentDestination) doReturn mockNavDestination
        testedStrategy.onActivityPaused(mockActivity)

        verify(mockRumMonitor).stopView(mockNavDestination, emptyMap())
    }

    @Test
    fun `does nothing onActivityPaused if currentDestination == null `() {
        whenever(mockNavController.currentDestination) doReturn null
        testedStrategy.onActivityPaused(mockActivity)

        verifyZeroInteractions(mockRumMonitor)
    }

    // endregion

    // FragmentLifecycleCallbacks

    @Test
    fun `will update Rum event time when fragment resumed with the resolved key`(forge: Forge) {
        // given
        whenever(mockNavController.currentDestination).thenReturn(mockNavDestination)
        val expectedLoadingTime = forge.aLong()
        val firsTimeLoading = forge.aBool()
        val expectedLoadingType =
            if (firsTimeLoading) {
                ViewEvent.LoadingType.FRAGMENT_DISPLAY
            } else {
                ViewEvent.LoadingType.FRAGMENT_REDISPLAY
            }
        whenever(mockViewLoadingTimer.getLoadingTime(mockNavDestination))
            .thenReturn(expectedLoadingTime)
        whenever(mockViewLoadingTimer.isFirstTimeLoading(mockNavDestination))
            .thenReturn(firsTimeLoading)
        val callback = setupFragmentCallbacks()

        // when
        callback.onFragmentResumed(mock(), mockFragment)

        // then
        verify(mockRumMonitor).updateViewLoadingTime(
            mockNavDestination,
            expectedLoadingTime,
            expectedLoadingType
        )
    }

    @Test
    fun `will do nothing when fragment resumed is NavHostFragment`(forge: Forge) {
        // given
        val navHostFragment: NavHostFragment = mock()
        whenever(mockNavController.currentDestination).thenReturn(mockNavDestination)
        val expectedLoadingTime = forge.aLong()
        val firsTimeLoading = forge.aBool()
        whenever(mockViewLoadingTimer.getLoadingTime(mockNavDestination))
            .thenReturn(expectedLoadingTime)
        whenever(mockViewLoadingTimer.isFirstTimeLoading(mockNavDestination))
            .thenReturn(firsTimeLoading)
        val callback = setupFragmentCallbacks()

        // when
        callback.onFragmentResumed(mock(), navHostFragment)

        // then
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `will do nothing when there is no currentDestination`(forge: Forge) {
        // given
        whenever(mockNavController.currentDestination).thenReturn(null)
        val expectedLoadingTime = forge.aLong()
        val firsTimeLoading = forge.aBool()
        whenever(mockViewLoadingTimer.getLoadingTime(mockNavDestination))
            .thenReturn(expectedLoadingTime)
        whenever(mockViewLoadingTimer.isFirstTimeLoading(mockNavDestination))
            .thenReturn(firsTimeLoading)
        val callback = setupFragmentCallbacks()

        // when
        callback.onFragmentResumed(mock(), mockFragment)

        // then
        verifyZeroInteractions(mockRumMonitor)
    }

    // region Internal

    private fun setupFragmentCallbacks():
        NavigationViewTrackingStrategy.NavControllerFragmentLifecycleCallbacks {
        // given
        testedStrategy.onActivityStarted(mockActivity)

        // then
        val argumentCaptor =
            argumentCaptor<NavigationViewTrackingStrategy.NavControllerFragmentLifecycleCallbacks>()
        verify(mockFragmentManager)
            .registerFragmentLifecycleCallbacks(argumentCaptor.capture(), eq(true))
        argumentCaptor.firstValue.setFieldValue("viewLoadingTimer", mockViewLoadingTimer)
        return argumentCaptor.firstValue
    }

    private fun getMockNavDestination(
        forge: Forge,
        name: String
    ): NavDestination {
        return forge.anElementFrom(
            mock<FragmentNavigator.Destination>().apply {
                whenever(getClassName()) doReturn name
            },
            mock<DialogFragmentNavigator.Destination>().apply {
                whenever(getClassName()) doReturn name
            },
            mock<ActivityNavigator.Destination>().apply {
                val componentName = ComponentName("", name)
                whenever(getComponent()) doReturn componentName
            }
        )
    }

    // endregion
}
