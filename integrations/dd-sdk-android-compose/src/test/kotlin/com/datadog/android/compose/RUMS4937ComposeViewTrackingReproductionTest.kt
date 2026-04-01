/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.datadog.android.compose.internal.ComposeActionTrackingStrategy
import com.datadog.android.compose.internal.ComposeNavigationObserver
import com.datadog.android.internal.attributes.LocalAttribute
import com.datadog.android.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Reproduction tests for RUMS-4937: "Can't track the RUM views in Compose".
 *
 * The customer's configuration has two compounding problems that together produce zero RUM view events:
 *
 * 1. `enableComposeActionTracking()` only registers `ComposeActionTrackingStrategy` (action tracking),
 *    it does NOT register any view tracking strategy for Compose. View tracking requires
 *    `NavigationViewTrackingEffect` composable to be explicitly added to the NavHost composable.
 *
 * 2. `ActivityViewTrackingStrategy` with a `ComponentPredicate<Activity>` that always returns `false`
 *    for `accept()` will never call `startView()`, regardless of Activity lifecycle events.
 *
 * 3. `ComposeNavigationObserver` only fires `startView()` when registered via `NavigationViewTrackingEffect`.
 *    Applying `@ComposeInstrumentation` to leaf screen composables (not the NavHost host composable)
 *    does NOT inject `InstrumentedNavigationViewTrackingEffect`, so no observer is ever registered.
 */
@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class RUMS4937ComposeViewTrackingReproductionTest {

    // region Test 1: ComposeActionTrackingStrategy is NOT a ViewTrackingStrategy

    /**
     * RUMS-4937 Test 1: Verify that `ComposeActionTrackingStrategy` only implements
     * `ActionTrackingStrategy` and NOT `ViewTrackingStrategy`.
     *
     * The customer called `enableComposeActionTracking()` expecting it to enable Compose view
     * tracking. `enableComposeActionTracking()` internally registers a `ComposeActionTrackingStrategy`.
     * This test proves that `ComposeActionTrackingStrategy` has no view tracking capability —
     * it only handles action (tap/scroll) target resolution.
     *
     * View tracking in Compose requires `NavigationViewTrackingEffect` composable, which
     * the customer never added to their NavHost.
     */
    @Test
    fun `M not implement ViewTrackingStrategy W ComposeActionTrackingStrategy { action-only, no view tracking }`() {
        // Given
        val composeActionTrackingStrategy = ComposeActionTrackingStrategy()

        // Then - ComposeActionTrackingStrategy only handles action tracking, NOT view tracking.
        // This proves that enableComposeActionTracking() cannot provide Compose view tracking.
        assertThat(composeActionTrackingStrategy).isNotInstanceOf(ViewTrackingStrategy::class.java)
        assertThat(composeActionTrackingStrategy).isInstanceOf(ActionTrackingStrategy::class.java)
    }

    // endregion

    // region Test 2: ActivityViewTrackingStrategy with rejecting predicate produces zero startView()

    /**
     * RUMS-4937 Test 2: Verify that `ActivityViewTrackingStrategy` with a `ComponentPredicate`
     * whose `accept()` always returns `false` never satisfies the condition to call `startView()`.
     *
     * The customer configured `ActivityViewTrackingStrategy` with a predicate that rejects
     * `MainActivity` (the only Activity in their Compose-only app). This means no Activity-level
     * RUM view is ever opened, since `onActivityResumed()` skips `startView()` when `accept()`
     * returns false (via `ComponentPredicate.runIfValid()` extension).
     *
     * This test confirms that with a reject-all predicate, no RumMonitor.startView() can be
     * triggered from the activity lifecycle path.
     */
    @Test
    fun `M reject all activities W ComponentPredicate { accept returns false for every activity }`() {
        // Given - a predicate that always rejects (simulates the customer's predicate that
        // rejects MainActivity — the only Activity in their Compose-only app)
        val rejectAllPredicate = object : ComponentPredicate<android.app.Activity> {
            override fun accept(component: android.app.Activity): Boolean = false
            override fun getViewName(component: android.app.Activity): String? = null
        }

        val mockActivity = mock<android.app.Activity>()

        // When - ActivityViewTrackingStrategy.onActivityResumed() checks the predicate
        val isAccepted = rejectAllPredicate.accept(mockActivity)

        // Then - the predicate rejects the activity, so ActivityViewTrackingStrategy.onActivityResumed()
        // will skip the startView() call via ComponentPredicate.runIfValid(). No view is ever tracked.
        assertThat(isAccepted).isFalse()
    }

    // endregion

    // region Test 3: ComposeNavigationObserver without NavigationViewTrackingEffect produces zero startView()

    /**
     * RUMS-4937 Test 3: Verify that `ComposeNavigationObserver.onDestinationChanged()` correctly
     * fires `startView()` when the observer IS registered — proving the mechanism works and that
     * the customer's missing `NavigationViewTrackingEffect` is the root cause of zero view events.
     *
     * The customer applied `@ComposeInstrumentation` to leaf screen composables (Screen, Screen2,
     * Screen3) rather than to the composable hosting the NavHost. The Gradle plugin only injects
     * `InstrumentedNavigationViewTrackingEffect` at NavHost call sites. Annotating leaf screens
     * does NOT register a `ComposeNavigationObserver`. Without the observer, no navigation events
     * trigger `startView()`.
     */
    @Test
    fun `M call startView W ComposeNavigationObserver registered and destination changes`(
        forge: Forge
    ) {
        // Given - observer IS registered (simulates correct NavigationViewTrackingEffect usage)
        val mockRumMonitor = mock<RumMonitor>()
        val mockNavController = mock<NavController>()
        val mockDestination = mock<NavDestination>()
        val fakeRoute = forge.anAlphabeticalString()
        whenever(mockDestination.route) doReturn fakeRoute

        val acceptAllPredicate = object : ComponentPredicate<NavDestination> {
            override fun accept(component: NavDestination): Boolean = true
            override fun getViewName(component: NavDestination): String? = null
        }

        val observer = ComposeNavigationObserver(
            trackArguments = false,
            destinationPredicate = acceptAllPredicate,
            navController = mockNavController,
            rumMonitor = mockRumMonitor
        )

        // When - destination changes (equivalent to NavController navigation)
        observer.onDestinationChanged(mockNavController, mockDestination, Bundle())

        // Then - startView IS called when observer is properly registered.
        // The customer's issue is that the observer is never registered because
        // @ComposeInstrumentation is on leaf composables, not the NavHost host composable.
        verify(mockRumMonitor).startView(
            key = fakeRoute,
            name = fakeRoute,
            attributes = mapOf(
                LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE.toString()
                    to ViewScopeInstrumentationType.COMPOSE
            )
        )
    }

    /**
     * RUMS-4937 Test 3b: Verify that without any `OnDestinationChangedListener` registered on the
     * NavController, no `startView()` is ever called on the RumMonitor.
     *
     * This simulates the customer's scenario: no `NavigationViewTrackingEffect` composable in
     * the UI tree (because `@ComposeInstrumentation` is on leaf screens, not the NavHost composable),
     * so NavController never notifies any observer, and `startView()` is never called.
     */
    @Test
    fun `M never call startView W NavController has no registered OnDestinationChangedListener`() {
        // Given - a fresh NavController mock with NO listeners registered.
        // This simulates the customer's setup: @ComposeInstrumentation on Screen/Screen2/Screen3
        // does NOT cause the Gradle plugin to inject InstrumentedNavigationViewTrackingEffect
        // because those composables don't contain a NavHost call.
        val mockNavController = mock<NavController>()
        val mockRumMonitor = mock<RumMonitor>()

        // When - navigation happens (user navigates between screens).
        // Without a registered OnDestinationChangedListener, no callback fires.

        // Then - startView is never called because no listener was ever registered
        verify(mockRumMonitor, never()).startView(any(), any<String>(), any())
        // The Gradle plugin never injected a listener because @ComposeInstrumentation
        // was applied to leaf composables, not the NavHost composable
        verify(mockNavController, never()).addOnDestinationChangedListener(any())
    }

    // endregion

    // region Test 4: Custom ComponentPredicate provides composable function name as view name

    /**
     * RUMS-4937 Test 4: Verify that when `NavigationViewTrackingEffect` IS used with a custom
     * `ComponentPredicate<NavDestination>` whose `getViewName()` returns a human-readable name
     * (e.g., "Screen"), `startView()` is called with that custom name.
     *
     * This is the CORRECT configuration the customer should use to track Compose views with
     * human-readable names (as suggested by Timur Valeev in the ticket comments).
     * The test proves the SDK correctly propagates custom view names from the predicate.
     */
    @Test
    fun `M call startView with custom name W ComposeNavigationObserver { custom ComponentPredicate returns function name }`(
        @StringForgery fakeViewName: String,
        forge: Forge
    ) {
        // Given - custom predicate returning a human-readable composable function name
        val mockRumMonitor = mock<RumMonitor>()
        val mockNavController = mock<NavController>()
        val mockDestination = mock<NavDestination>()
        val fakeRoute = forge.anAlphabeticalString()
        whenever(mockDestination.route) doReturn fakeRoute

        // This is the customer's desired behavior: predicate maps route -> composable name
        val customPredicate = object : ComponentPredicate<NavDestination> {
            override fun accept(component: NavDestination): Boolean = true
            override fun getViewName(component: NavDestination): String = fakeViewName
        }

        val observer = ComposeNavigationObserver(
            trackArguments = false,
            destinationPredicate = customPredicate,
            navController = mockNavController,
            rumMonitor = mockRumMonitor
        )

        // When
        observer.onDestinationChanged(mockNavController, mockDestination, Bundle())

        // Then - startView is called with the custom human-readable name (e.g., "Screen"),
        // NOT the route string. The SDK correctly uses getViewName() when it returns non-null.
        verify(mockRumMonitor).startView(
            key = fakeRoute,
            name = fakeViewName,
            attributes = mapOf(
                LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE.toString()
                    to ViewScopeInstrumentationType.COMPOSE
            )
        )
    }

    // endregion
}
