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
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
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
    lateinit var mockNavView: View

    @Mock
    lateinit var mockNavHostFragment: NavHostFragment

    @Mock
    lateinit var mockNavController: NavController

    @Mock
    lateinit var mockFragmentManager: FragmentManager

    lateinit var mockNavDestination: NavDestination

    @Mock
    lateinit var mockFragment: Fragment

    @Mock
    lateinit var mockPredicate: ComponentPredicate<NavDestination>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @IntForgery
    var fakeNavViewId: Int = 0

    @StringForgery
    lateinit var fakeDestinationName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockActivity.supportFragmentManager).thenReturn(mockFragmentManager)
        if (forge.aBool()) {
            // nav host in FragmentContainerView case
            whenever(mockFragmentManager.findFragmentById(fakeNavViewId)) doReturn mockNavHostFragment
            whenever(mockNavHostFragment.navController) doReturn mockNavController
        } else {
            // nav host in fragment case
            doReturn(mockNavView).whenever(mockActivity).findViewById<View>(fakeNavViewId)
            whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn mockNavController
        }
        mockNavDestination = mockNavDestination(forge, fakeDestinationName)

        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockRumFeatureScope.unwrap<RumFeature>()) doReturn mock()
        whenever(
            rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedStrategy = NavigationViewTrackingStrategy(fakeNavViewId, true, mockPredicate)
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
    }

    // region ActivityLifecycleTrackingStrategy

    @Test
    fun `when register it will register as lifecycle callback`() {
        testedStrategy = NavigationViewTrackingStrategy(fakeNavViewId, true, mockPredicate)

        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        verify(mockAppContext).registerActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when unregister it will remove itself  as lifecycle callback`() {
        testedStrategy.unregister(mockAppContext)

        verify(mockAppContext).unregisterActivityLifecycleCallbacks(testedStrategy)
    }

    @Test
    fun `when register called with non application context will do nothing`() {
        val mockNotAppContext = mock<Context>()

        testedStrategy.register(rumMonitor.mockSdkCore, mockNotAppContext)

        verifyNoInteractions(mockNotAppContext)
    }

    @Test
    fun `when unregister called with non application context will do nothing`() {
        val mockNotAppContext = mock<Context>()

        testedStrategy.unregister(mockNotAppContext)

        verifyNoInteractions(mockNotAppContext)
    }

    // endregion

    // region NavigationViewTrackingStrategy

    @Test
    fun `registers navigation listener onActivityStarted`() {
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)
        testedStrategy.onActivityStarted(mockActivity)

        verify(mockNavController).addOnDestinationChangedListener(testedStrategy)
    }

    @Test
    fun `registers navigation listener when nav controller added after activity started {nav + fragment}`() {
        whenever(mockFragmentManager.findFragmentById(fakeNavViewId)) doReturn null

        whenever(mockActivity.findViewById<View>(fakeNavViewId)) doReturn mockNavView
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)
        testedStrategy.onActivityStarted(mockActivity)
        verifyNoInteractions(mockNavController)

        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn mockNavController
        testedStrategy.startTracking()
        verify(mockNavController).addOnDestinationChangedListener(testedStrategy)
    }

    @Test
    fun `registers navigation listener when nav controller added after activity started{nav + container view}`() {
        whenever(mockFragmentManager.findFragmentById(fakeNavViewId)) doReturn null
        whenever(mockActivity.findViewById<View>(fakeNavViewId)) doReturn null
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)
        testedStrategy.onActivityStarted(mockActivity)
        verifyNoInteractions(mockNavController)

        whenever(mockFragmentManager.findFragmentById(fakeNavViewId)) doReturn mockNavHostFragment
        whenever(mockNavHostFragment.navController) doReturn mockNavController
        testedStrategy.startTracking()
        verify(mockNavController).addOnDestinationChangedListener(testedStrategy)
    }

    @Test
    fun `registers fragment lifecycle callback onActivityStarted`() {
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)
        testedStrategy.onActivityStarted(mockActivity)

        verify(mockFragmentManager).registerFragmentLifecycleCallbacks(
            any<NavLifecycleCallbacks>(),
            eq(true)
        )
    }

    @Test
    fun `unregisters navigation listener onActivityStopped if activity was started`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)
        testedStrategy.onActivityStarted(mockActivity)

        // When
        testedStrategy.onActivityStopped(mockActivity)

        // Then
        verify(mockNavController).removeOnDestinationChangedListener(testedStrategy)
    }

    @Test
    fun `doesn't unregister navigation listener onActivityStopped if activity not started`() {
        testedStrategy.onActivityStopped(mockActivity)

        verifyNoInteractions(mockNavController)
    }

    @Test
    fun `unregisters fragment lifecycle callback onActivityStopped`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
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

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `does nothing onActivityStopped when no NavHost present`() {
        testedStrategy.onActivityStopped(mock())

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `does nothing onActivityPaused when no NavHost present`() {
        testedStrategy.onActivityPaused(mock())

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `does nothing onActivityStarted when no NavController present`() {
        whenever(mockFragmentManager.findFragmentById(fakeNavViewId)) doReturn null
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null

        testedStrategy.onActivityStarted(mockActivity)

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `does nothing onActivityStopped when no NavController present`() {
        whenever(mockFragmentManager.findFragmentById(fakeNavViewId)) doReturn null
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null

        testedStrategy.onActivityStopped(mockActivity)

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `does nothing onActivityPaused when no NavController present`() {
        whenever(mockFragmentManager.findFragmentById(fakeNavViewId)) doReturn null
        whenever(mockNavView.getTag(R.id.nav_controller_view_tag)) doReturn null
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        testedStrategy.onActivityPaused(mockActivity)

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M start view W onDestinationChanged()`() {
        whenever(mockPredicate.accept(mockNavDestination)) doReturn true
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, null)

        verify(rumMonitor.mockInstance).startView(
            mockNavDestination,
            fakeDestinationName,
            mapOf(ViewScopeInstrumentationType.FRAGMENT.key.string to ViewScopeInstrumentationType.FRAGMENT)
        )
    }

    @Test
    fun `M start view W onDestinationChanged() {destination not tracked}`() {
        whenever(mockPredicate.accept(mockNavDestination)) doReturn false
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, null)

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M start view W onDestinationChanged() {custom name}`(
        @StringForgery customName: String
    ) {
        whenever(mockPredicate.accept(mockNavDestination)) doReturn true
        whenever(mockPredicate.getViewName(mockNavDestination)) doReturn customName
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, null)

        verify(rumMonitor.mockInstance).startView(
            mockNavDestination,
            customName,
            mapOf(ViewScopeInstrumentationType.FRAGMENT.key.string to ViewScopeInstrumentationType.FRAGMENT)
        )
    }

    @Test
    fun `M start view W onDestinationChanged() {with arguments}`(
        forge: Forge
    ) {
        whenever(mockPredicate.accept(mockNavDestination)) doReturn true
        val arguments = Bundle()
        val expectedAttrs = mutableMapOf<String, Any?>(
            ViewScopeInstrumentationType.FRAGMENT.key.string to ViewScopeInstrumentationType.FRAGMENT
        )
        repeat(10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
            expectedAttrs["view.arguments.$key"] = value
        }
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        verify(rumMonitor.mockInstance).startView(
            mockNavDestination,
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
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        val arguments = Bundle()
        repeat(10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
        }

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        verify(rumMonitor.mockInstance).startView(
            mockNavDestination,
            fakeDestinationName,
            mapOf(ViewScopeInstrumentationType.FRAGMENT.key.string to ViewScopeInstrumentationType.FRAGMENT)
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
        testedStrategy.register(rumMonitor.mockSdkCore, mockActivity)

        testedStrategy.onDestinationChanged(mockNavController, mockNavDestination, null)
        whenever(mockNavController.currentDestination) doReturn mockNavDestination
        testedStrategy.onDestinationChanged(mockNavController, newDestination, null)

        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startView(
                mockNavDestination,
                fakeDestinationName,
                mapOf(ViewScopeInstrumentationType.FRAGMENT.key.string to ViewScopeInstrumentationType.FRAGMENT)
            )
            verify(rumMonitor.mockInstance).startView(
                newDestination,
                newDestinationName,
                mapOf(ViewScopeInstrumentationType.FRAGMENT.key.string to ViewScopeInstrumentationType.FRAGMENT)
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

        verifyNoInteractions(rumMonitor.mockInstance)
    }

    // endregion

    // FragmentLifecycleCallbacks

    @Test
    fun `will do nothing when fragment resumed is NavHostFragment`() {
        // Given
        val navHostFragment: NavHostFragment = mock()
        whenever(mockNavController.currentDestination).thenReturn(mockNavDestination)
        val callback = setupFragmentCallbacks()

        // When
        callback.onFragmentResumed(mock(), navHostFragment)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `will do nothing when there is no currentDestination`() {
        // Given
        whenever(mockNavController.currentDestination).thenReturn(null)
        val callback = setupFragmentCallbacks()

        // When
        callback.onFragmentResumed(mock(), mockFragment)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    // region Internal

    private fun setupFragmentCallbacks(): NavLifecycleCallbacks {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        return argumentCaptor<NavLifecycleCallbacks> {
            verify(mockFragmentManager).registerFragmentLifecycleCallbacks(capture(), eq(true))
        }.firstValue
    }

    private fun mockNavDestination(
        forge: Forge,
        name: String
    ): NavDestination {
        return forge.anElementFrom(
            mock<FragmentNavigator.Destination>().apply {
                whenever(className) doReturn name
            },
            mock<DialogFragmentNavigator.Destination>().apply {
                whenever(className) doReturn name
            },
            mock<ActivityNavigator.Destination>().apply {
                val componentName = ComponentName("", name)
                whenever(component) doReturn componentName
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
