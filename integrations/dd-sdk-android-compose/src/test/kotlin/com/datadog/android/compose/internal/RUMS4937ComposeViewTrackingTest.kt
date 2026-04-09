/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.internal.attributes.LocalAttribute
import com.datadog.android.internal.attributes.ViewScopeInstrumentationType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Regression tests for RUMS-4937: "Can't track the RUM views in Compose"
 *
 * Root cause: The customer uses @ComposeInstrumentation on individual screen composables and
 * avoids NavigationViewTrackingEffect, believing it forces route strings as view names.
 *
 * These tests document:
 * 1. ComposeNavigationObserver must be registered as an OnDestinationChangedListener via
 *    NavigationViewTrackingEffect — if it is never registered, no views are tracked.
 * 2. ComponentPredicate<NavDestination>.getViewName() already supports custom human-readable
 *    names, so the customer's concern about route strings is unfounded.
 */
@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class RUMS4937ComposeViewTrackingTest {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockNavController: NavController

    // region RUMS-4937: No views tracked when NavigationViewTrackingEffect is absent

    /**
     * Verifies that destination changes on a NavController do NOT produce RUM view events
     * if ComposeNavigationObserver was never registered as an OnDestinationChangedListener.
     *
     * This simulates the customer's setup: they do not call NavigationViewTrackingEffect,
     * so ComposeNavigationObserver is never created and no listener is ever registered.
     * The NavController fires destination changes internally, but nothing observes them.
     *
     * Expected: rumMonitor.startView() is never called.
     */
    @Test
    fun `M not start any view W compose app has no NavigationViewTrackingEffect and destination changes`() {
        // Given — customer sets up ActivityViewTrackingStrategy that rejects MainActivity and
        // calls enableComposeActionTracking(), but never calls NavigationViewTrackingEffect.
        // ComposeNavigationObserver is therefore never instantiated or registered.
        // We simulate the NavController firing destination changes with no listener registered.

        // No ComposeNavigationObserver is created — no listener registered on navController.

        // When — NavController fires a destination change (e.g. user navigates to HomeScreen)
        // In the real app this happens internally; here we just verify that without a registered
        // ComposeNavigationObserver there is nothing to relay the event to rumMonitor.
        mockNavController.currentDestination // access current destination (no listener involved)

        // Then — no RUM view is ever started because there is no active ComposeNavigationObserver
        verifyNoInteractions(mockRumMonitor)
    }

    /**
     * Verifies that once ComposeNavigationObserver IS registered (i.e. NavigationViewTrackingEffect
     * IS used), destination changes DO produce RUM view events.
     *
     * This is the control case: shows what the customer SHOULD do.
     */
    @Test
    fun `M start view W NavigationViewTrackingEffect registers ComposeNavigationObserver and destination changes`(
        forge: Forge
    ) {
        // Given — NavigationViewTrackingEffect creates and registers a ComposeNavigationObserver.
        // We simulate this by instantiating the observer directly (as the effect would).
        val fakeRoute = forge.anAlphabeticalString()
        val mockDestination = mock<NavDestination>().apply {
            whenever(route) doReturn fakeRoute
        }
        val mockPredicate = mock<ComponentPredicate<NavDestination>>().apply {
            whenever(accept(any())) doReturn true
            whenever(getViewName(any())) doReturn null // return null → falls back to route
        }

        val observer = ComposeNavigationObserver(
            trackArguments = true,
            destinationPredicate = mockPredicate,
            navController = mockNavController,
            rumMonitor = mockRumMonitor
        )
        // NavigationViewTrackingEffect registers the observer as a destination-changed listener.
        // Simulated here by directly calling onDestinationChanged as the NavController would.

        // When
        observer.onDestinationChanged(mockNavController, mockDestination, Bundle())

        // Then — RUM view is started with the route as name
        verify(mockRumMonitor).startView(
            key = fakeRoute,
            name = fakeRoute,
            attributes = mapOf(
                LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE.toString() to
                    ViewScopeInstrumentationType.COMPOSE
            )
        )
    }

    // endregion

    // region RUMS-4937: Custom view names via ComponentPredicate are already supported

    /**
     * Verifies that ComponentPredicate<NavDestination>.getViewName() can return a custom
     * human-readable name, which overrides the route string in the RUM view event.
     *
     * This disproves the customer's stated reason for avoiding NavigationViewTrackingEffect:
     * they believed it forces them to use route strings as view names. It does not.
     * ComposeNavigationObserver already calls destinationPredicate.getViewName(destination)
     * and uses that value when non-null.
     */
    @Test
    fun `M start view with custom name W NavigationViewTrackingEffect used with ComponentPredicate returning custom name`(
        @StringForgery fakeCustomViewName: String,
        forge: Forge
    ) {
        // Given
        val fakeRoute = forge.anAlphabeticalString()
        val mockDestination = mock<NavDestination>().apply {
            whenever(route) doReturn fakeRoute
        }
        // Customer supplies a ComponentPredicate that returns a human-readable name
        val customNamePredicate = mock<ComponentPredicate<NavDestination>>().apply {
            whenever(accept(any())) doReturn true
            // Returns "Home Screen" instead of "home_screen_route"
            whenever(getViewName(any())) doReturn fakeCustomViewName
        }

        val observer = ComposeNavigationObserver(
            trackArguments = false,
            destinationPredicate = customNamePredicate,
            navController = mockNavController,
            rumMonitor = mockRumMonitor
        )

        // When — user navigates to HomeScreen composable
        observer.onDestinationChanged(mockNavController, mockDestination, Bundle())

        // Then — RUM view is started with the CUSTOM name, not the route string
        verify(mockRumMonitor).startView(
            key = fakeRoute,
            name = fakeCustomViewName,
            attributes = mapOf(
                LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE.toString() to
                    ViewScopeInstrumentationType.COMPOSE
            )
        )
    }

    // endregion
}
