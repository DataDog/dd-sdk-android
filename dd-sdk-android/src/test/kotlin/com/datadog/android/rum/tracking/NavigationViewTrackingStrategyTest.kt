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
import com.datadog.android.Datadog
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.tracking.ViewLoadingTimer
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.NoOpSdkCore
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
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
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
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

internal typealias NavLifecycleCallbacks =
    NavigationViewTrackingStrategy.NavControllerFragmentLifecycleCallbacks

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
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
    lateinit var mockViewLoadingTimer: ViewLoadingTimer

    @Mock
    lateinit var mockFragment: Fragment

    @Mock
    lateinit var mockPredicate: ComponentPredicate<NavDestination>

    lateinit var mockNavigationKey: NavigationViewTrackingStrategy.NavigationKey

    @IntForgery
    var fakeNavViewId: Int = 0

    @StringForgery
    lateinit var fakeDestinationName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockActivity.supportFragmentManager).thenReturn(mockFragmentManager)
        doReturn(mockNavView).whenever(mockActivity).findViewById<View>(fakeNavViewId)
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn mockNavController
        mockNavDestination = mockNavDestination(forge, fakeDestinationName)

        val mockCore = mock<DatadogCore>()
        whenever(mockCore.rumFeature) doReturn mock<RumFeature>()
        Datadog.globalSdkCore = mockCore

        mockNavigationKey = NavigationViewTrackingStrategy.NavigationKey(mockNavController, mockNavDestination)

        testedStrategy = NavigationViewTrackingStrategy(fakeNavViewId, true, mockPredicate)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.globalSdkCore = NoOpSdkCore()
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
    fun `registers navigation listener when nav controller added after activity started`() {
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null
        testedStrategy.onActivityStarted(mockActivity)
        verifyZeroInteractions(mockNavController)

        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn mockNavController
        testedStrategy.startTracking()
        verify(mockNavController).addOnDestinationChangedListener(testedStrategy)
    }

    @Test
    fun `registers fragment lifecycle callback onActivityStarted`() {
        testedStrategy.onActivityStarted(mockActivity)

        verify(mockFragmentManager).registerFragmentLifecycleCallbacks(
            any<NavLifecycleCallbacks>(),
            eq(true)
        )
    }

    @Test
    fun `unregisters navigation listener onActivityStopped if activity was started`() {
        testedStrategy.onActivityStarted(mockActivity)
        testedStrategy.onActivityStopped(mockActivity)

        verify(mockNavController).removeOnDestinationChangedListener(testedStrategy)
    }

    @Test
    fun `doesn't unregister navigation listener onActivityStopped if activity not started`() {
        testedStrategy.onActivityStopped(mockActivity)

        verifyZeroInteractions(mockNavController)
    }

    @Test
    fun `unregisters fragment lifecycle callback onActivityStopped`() {
        // Given
        testedStrategy.onActivityStarted(mockActivity)

        // When
        testedStrategy.onActivityStopped(mockActivity)

        // Then
        argumentCaptor<NavLifecycleCallbacks> {
            verify(mockFragmentManager).registerFragmentLifecycleCallbacks(capture(), eq(true))
            verify(mockFragmentManager).unregisterFragmentLifecycleCallbacks(firstValue)
        }
    }

    @Test
    fun `does nothing onActivityStarted when no NavHost present`() {
        testedStrategy.onActivityStarted(mock())

        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `does nothing onActivityStopped when no NavHost present`() {
        testedStrategy.onActivityStopped(mock())

        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `does nothing onActivityPaused when no NavHost present`() {
        testedStrategy.onActivityPaused(mock())

        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `does nothing onActivityStarted when no NavController present`() {
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null

        testedStrategy.onActivityStarted(mockActivity)

        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `does nothing onActivityStopped when no NavController present`() {
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null

        testedStrategy.onActivityStopped(mockActivity)

        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `does nothing onActivityPaused when no NavController present`() {
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null

        testedStrategy.onActivityPaused(mockActivity)

        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M start view W onDestinationChanged()`() {
        whenever(mockPredicate.accept(mockNavDestination)) doReturn true

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, null)

        verify(rumMonitor.mockInstance).startView(
            mockNavigationKey,
            fakeDestinationName,
            emptyMap()
        )
    }

    @Test
    fun `M start view W onDestinationChanged() {destination not tracked}`() {
        whenever(mockPredicate.accept(mockNavDestination)) doReturn false

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, null)

        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M start view W onDestinationChanged() {custom name}`(
        @StringForgery customName: String
    ) {
        whenever(mockPredicate.accept(mockNavDestination)) doReturn true
        whenever(mockPredicate.getViewName(mockNavDestination)) doReturn customName

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, null)

        verify(rumMonitor.mockInstance).startView(mockNavigationKey, customName, emptyMap())
    }

    @Test
    fun `M start view W onDestinationChanged() {with arguments}`(
        forge: Forge
    ) {
        whenever(mockPredicate.accept(mockNavDestination)) doReturn true
        val arguments = Bundle()
        val expectedAttrs = mutableMapOf<String, Any?>()
        repeat(10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
            expectedAttrs["view.arguments.$key"] = value
        }

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        verify(rumMonitor.mockInstance).startView(
            mockNavigationKey,
            fakeDestinationName,
            expectedAttrs
        )
    }

    @Test
    fun `starts view onDestinationChanged with arguments un-tracked`(
        forge: Forge
    ) {
        whenever(mockPredicate.accept(mockNavDestination)) doReturn true
        testedStrategy = NavigationViewTrackingStrategy(fakeNavViewId, false)

        val arguments = Bundle()
        repeat(10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
        }

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        verify(rumMonitor.mockInstance).startView(
            mockNavigationKey,
            fakeDestinationName,
            emptyMap()
        )
    }

    @Test
    fun `start new view onDestinationChanged`(
        forge: Forge,
        @StringForgery newDestinationName: String
    ) {
        val newDestination = mockNavDestination(forge, newDestinationName)
        whenever(mockPredicate.accept(mockNavDestination)) doReturn true
        whenever(mockPredicate.accept(newDestination)) doReturn true

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, null)
        whenever(mockNavController.currentDestination) doReturn mockNavDestination
        testedStrategy.onDestinationChanged(mockNavController, newDestination, null)

        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startView(
                mockNavigationKey,
                fakeDestinationName,
                emptyMap()
            )
            verify(rumMonitor.mockInstance).startView(
                NavigationViewTrackingStrategy.NavigationKey(mockNavController, newDestination),
                newDestinationName,
                emptyMap()
            )
        }
    }

    @Test
    fun `stops current view onActivityPaused`() {
        whenever(mockNavController.currentDestination) doReturn mockNavDestination
        testedStrategy.onActivityPaused(mockActivity)

        verify(rumMonitor.mockInstance).stopView(mockNavDestination, emptyMap())
    }

    @Test
    fun `does nothing onActivityPaused if currentDestination == null `() {
        whenever(mockNavController.currentDestination) doReturn null
        testedStrategy.onActivityPaused(mockActivity)

        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    // endregion

    // FragmentLifecycleCallbacks

    @Test
    fun `will update Rum event time when fragment resumed with the resolved key`(forge: Forge) {
        // Given
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

        // When
        callback.onFragmentResumed(mock(), mockFragment)

        // Then
        verify(rumMonitor.mockInstance).updateViewLoadingTime(
            mockNavDestination,
            expectedLoadingTime,
            expectedLoadingType
        )
    }

    @Test
    fun `will do nothing when fragment resumed is NavHostFragment`(forge: Forge) {
        // Given
        val navHostFragment: NavHostFragment = mock()
        whenever(mockNavController.currentDestination).thenReturn(mockNavDestination)
        val expectedLoadingTime = forge.aLong()
        val firsTimeLoading = forge.aBool()
        whenever(mockViewLoadingTimer.getLoadingTime(mockNavDestination))
            .thenReturn(expectedLoadingTime)
        whenever(mockViewLoadingTimer.isFirstTimeLoading(mockNavDestination))
            .thenReturn(firsTimeLoading)
        val callback = setupFragmentCallbacks()

        // When
        callback.onFragmentResumed(mock(), navHostFragment)

        // Then
        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `will do nothing when there is no currentDestination`(forge: Forge) {
        // Given
        whenever(mockNavController.currentDestination).thenReturn(null)
        val expectedLoadingTime = forge.aLong()
        val firsTimeLoading = forge.aBool()
        whenever(mockViewLoadingTimer.getLoadingTime(mockNavDestination))
            .thenReturn(expectedLoadingTime)
        whenever(mockViewLoadingTimer.isFirstTimeLoading(mockNavDestination))
            .thenReturn(firsTimeLoading)
        val callback = setupFragmentCallbacks()

        // When
        callback.onFragmentResumed(mock(), mockFragment)

        // Then
        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    // region Internal

    private fun setupFragmentCallbacks(): NavLifecycleCallbacks {
        // Given
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        return argumentCaptor<NavLifecycleCallbacks> {
            verify(mockFragmentManager).registerFragmentLifecycleCallbacks(capture(), eq(true))
            firstValue.viewLoadingTimer = mockViewLoadingTimer
        }.firstValue
    }

    private fun mockNavDestination(
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

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
