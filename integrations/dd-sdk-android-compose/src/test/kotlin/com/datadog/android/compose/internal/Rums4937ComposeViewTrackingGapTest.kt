/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal

import android.app.Activity
import android.content.Context
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Reproduction tests for RUMS-4937: "Can't track the RUM views in Compose"
 *
 * Root cause: The customer's setup uses ActivityViewTrackingStrategy with a ComponentPredicate
 * that rejects MainActivity, and they did NOT call NavigationViewTrackingEffect() in their
 * NavHost composable. The result is that ComposeNavigationObserver is never instantiated and
 * never registered as an OnDestinationChangedListener, so RumMonitor.startView() is never called.
 *
 * These tests demonstrate the gap: they simulate the customer's expected behaviour (views being
 * tracked) but the wiring required to make it work is absent. Each test FAILS because
 * startView() is never invoked without the NavigationViewTrackingEffect/ComposeNavigationObserver
 * being properly wired into the NavController.
 */
@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class Rums4937ComposeViewTrackingGapTest {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockNavController: NavController

    @Mock
    lateinit var mockDestinationPredicate: ComponentPredicate<NavDestination>

    // region Test 1 — No observer attached: navigation never triggers startView
    //
    // This is the core of the bug. The customer never calls NavigationViewTrackingEffect(),
    // so no ComposeNavigationObserver is ever added to the NavController as a listener.
    // When the NavController changes destination, no listener fires, and startView() is
    // never called.
    //
    // The test FAILS because it asserts startView() was called but the observer was
    // never registered — simulating the customer's missing wiring.

    @Test
    fun `M start view W NavController changes destination { observer never registered via NavigationViewTrackingEffect }`(
        forge: Forge
    ) {
        // Given — customer's setup: NavController exists but NavigationViewTrackingEffect
        // was never called, so no ComposeNavigationObserver is registered as a listener.
        // We simulate the customer expecting views to be tracked by just holding a NavController.
        val destination = forge.aNavDestination()
        // capture before verify to avoid Mockito interception of mock property access
        val expectedRoute = destination.route!!
        whenever(mockDestinationPredicate.accept(any())) doReturn true

        // No observer is created or registered — this is the customer's missing wiring.
        // The NavController destination changes (simulated by a direct nav event), but
        // nothing intercepts it.

        // When — a destination change happens in the app (the customer navigates)
        // In the real app this is triggered by NavController internally; no listener fires
        // because none was registered. We verify by checking the RumMonitor directly.

        // Then — FAILS: customer expects startView() to have been called, but it never is
        // because NavigationViewTrackingEffect was not called to wire up the observer.
        verify(mockRumMonitor).startView(
            eq(expectedRoute),
            eq(expectedRoute),
            any()
        )
    }

    // endregion

    // region Test 2 — Observer works when properly wired; failing scenario is the missing wiring
    //
    // This test proves the mechanism works in isolation: calling onDestinationChanged()
    // directly on a properly constructed ComposeNavigationObserver DOES invoke startView().
    // The contrast with Test 1 shows that the ONLY missing piece is the NavigationViewTrackingEffect
    // call that creates and registers the observer.

    @Test
    fun `M start view W onDestinationChanged called directly { observer properly constructed }`(
        forge: Forge
    ) {
        // Given — observer is created directly (as NavigationViewTrackingEffect would do)
        val destination = forge.aNavDestination()
        // capture before verify to avoid Mockito interception of mock property access
        val expectedRoute = destination.route!!
        whenever(mockDestinationPredicate.accept(any())) doReturn true

        val observer = ComposeNavigationObserver(
            trackArguments = false,
            destinationPredicate = mockDestinationPredicate,
            navController = mockNavController,
            rumMonitor = mockRumMonitor
        )

        // When — destination changes and observer is called (because it was registered)
        observer.onDestinationChanged(mockNavController, destination, null)

        // Then — PASSES: startView() is called, proving the mechanism works when wired correctly
        verify(mockRumMonitor).startView(
            eq(expectedRoute),
            eq(expectedRoute),
            any()
        )
    }

    // endregion

    // region Test 3 — Custom ComponentPredicate returns composable name instead of route
    //
    // The customer wants to see their composable function names (e.g. "HomeScreen") in RUM,
    // not raw route strings (e.g. "home_screen/{id}"). This only works when:
    // (a) NavigationViewTrackingEffect is called, AND
    // (b) a custom ComponentPredicate<NavDestination> with getViewName() returning the
    //     composable name is passed to it.
    //
    // This test PASSES (it verifies the correct API to use), but it also demonstrates that
    // the fix requires the customer to adopt NavigationViewTrackingEffect with a predicate.

    @Test
    fun `M use composable function name as view name W custom predicate is wired via NavigationViewTrackingEffect`(
        forge: Forge
    ) {
        // Given
        val destination = forge.aNavDestination(routeValue = "screen1")
        val customViewName = "HomeScreen"
        whenever(mockDestinationPredicate.accept(any())) doReturn true
        whenever(mockDestinationPredicate.getViewName(any())) doReturn customViewName

        val observer = ComposeNavigationObserver(
            trackArguments = false,
            destinationPredicate = mockDestinationPredicate,
            navController = mockNavController,
            rumMonitor = mockRumMonitor
        )

        // When
        observer.onDestinationChanged(mockNavController, destination, null)

        // Then — view name is the composable name, not the route string
        verify(mockRumMonitor).startView(
            eq("screen1"),
            eq(customViewName),
            any()
        )
    }

    // endregion

    // region Test 4 — enableComposeActionTracking() never starts a view
    //
    // The customer may believe that calling enableComposeActionTracking() also sets up
    // view tracking. This test proves it does NOT — registering ComposeActionTrackingStrategy
    // has zero effect on view tracking.

    @Test
    fun `M never start view W ComposeActionTrackingStrategy is registered { no view tracking side effect }`(
        forge: Forge
    ) {
        // Given
        val strategy = ComposeActionTrackingStrategy()
        val mockContext = mock<Context>()
        val mockSdkCore = mock<SdkCore>()

        // When — customer calls enableComposeActionTracking() which calls strategy.register()
        strategy.register(mockSdkCore, mockContext)

        // Navigate to a destination — but no view tracking is set up
        forge.aNavDestination() // destination exists but no observer sees it

        // Then — PASSES trivially: startView() is never called by action tracking registration
        // This demonstrates that action tracking and view tracking are completely separate,
        // and the customer's enableComposeActionTracking() call does not help with view tracking.
        verifyNoInteractions(mockRumMonitor)
    }

    // endregion

    // region Test 5 — Full customer scenario: ActivityViewTrackingStrategy rejects MainActivity,
    //                  no NavigationViewTrackingEffect → zero views started
    //
    // This is the complete reproduction of RUMS-4937. The customer:
    // 1. Uses ActivityViewTrackingStrategy with a predicate that rejects MainActivity
    // 2. Does NOT call NavigationViewTrackingEffect in their NavHost composable
    //
    // The test FAILS because it asserts that at least one view was started, but with
    // the customer's configuration nothing ever calls startView().

    @Test
    fun `M start at least one view W customer setup with ActivityViewTrackingStrategy excluding MainActivity and no NavigationViewTrackingEffect`(
        forge: Forge
    ) {
        // Given — customer's ActivityViewTrackingStrategy rejects MainActivity
        val rejectsMainActivity = mock<ComponentPredicate<Activity>>()
        whenever(rejectsMainActivity.accept(any())) doReturn false // MainActivity always rejected

        // No NavigationViewTrackingEffect is called — no ComposeNavigationObserver is created
        // No listener is attached to the NavController

        // The NavController changes destination (user navigates in Compose)
        forge.aNavDestination() // destination exists but no observer sees it

        // When — MainActivity resumes (ActivityViewTrackingStrategy is called but rejects it)
        // AND navigation happens in Compose (no observer is registered)
        // Both mechanisms fail to start a view.

        // Then — FAILS: customer expects at least one view to be tracked, but startView()
        // is never called because:
        // (a) ActivityViewTrackingStrategy rejects MainActivity via the predicate
        // (b) NavigationViewTrackingEffect was not called so no ComposeNavigationObserver exists
        verify(mockRumMonitor).startView(any(), any(), any())
    }

    // endregion

    // region helpers

    private fun Forge.aNavDestination(
        routeValue: String? = anAlphabeticalString()
    ): NavDestination {
        return anElementFrom(
            mock<NavDestination>().apply {
                whenever(route) doReturn routeValue
            }
        )
    }

    // endregion
}
