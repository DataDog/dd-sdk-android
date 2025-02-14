/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.datadog.android.core.internal.attributes.LocalAttribute
import com.datadog.android.core.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ComposeNavigationObserverTest {

    private lateinit var testedObserver: ComposeNavigationObserver

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockNavController: NavController

    @Mock
    lateinit var mockPredicate: ComponentPredicate<NavDestination>

    @BeforeEach
    fun setUp() {
        whenever(mockPredicate.accept(any())) doReturn true

        testedObserver = ComposeNavigationObserver(
            trackArguments = true,
            destinationPredicate = mockPredicate,
            navController = mockNavController,
            rumMonitor = mockRumMonitor
        )
    }

    // region NavController.OnDestinationChangedListener callbacks

    @Test
    fun `M start view W destination is changed`(
        forge: Forge
    ) {
        // Given
        val arguments = Bundle()
        val expectedAttrs = mutableMapOf<String, Any?>(
            LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE.toString() to ViewScopeInstrumentationType.COMPOSE
        )
        repeat(10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
            expectedAttrs["view.arguments.$key"] = value
        }

        val mockNavDestination = forge.aNavDestination()

        // When
        testedObserver.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        // Then
        verify(mockRumMonitor).startView(
            mockNavDestination.route!!,
            mockNavDestination.route!!,
            expectedAttrs
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `M start view W destination is changed { custom view name }`(
        @StringForgery fakeViewName: String,
        forge: Forge
    ) {
        // Given

        whenever(mockPredicate.getViewName(any())) doReturn fakeViewName

        val arguments = Bundle()
        val expectedAttrs = mutableMapOf<String, Any?>(
            LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE.toString() to ViewScopeInstrumentationType.COMPOSE
        )
        repeat(10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
            expectedAttrs["view.arguments.$key"] = value
        }

        val mockNavDestination = forge.aNavDestination()

        // When
        testedObserver.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        // Then
        verify(mockRumMonitor).startView(
            mockNavDestination.route!!,
            fakeViewName,
            expectedAttrs
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `M start view without arguments W destination is changed { trackArguments = false }`(
        forge: Forge
    ) {
        // Given
        val arguments = Bundle()
        repeat(10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
        }

        val mockNavDestination = forge.aNavDestination()

        testedObserver = ComposeNavigationObserver(
            trackArguments = false,
            destinationPredicate = mockPredicate,
            navController = mockNavController,
            rumMonitor = mockRumMonitor
        )

        // When
        testedObserver.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        // Then
        verify(mockRumMonitor).startView(
            mockNavDestination.route!!,
            mockNavDestination.route!!,
            mapOf(LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE.toString() to ViewScopeInstrumentationType.COMPOSE)
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `M not start view W destination is changed { predicate didn't accept }`(
        forge: Forge
    ) {
        // Given
        whenever(mockPredicate.accept(any())) doReturn false
        val arguments = Bundle()
        val mockNavDestination = forge.aNavDestination()

        // When
        testedObserver.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        // Then
        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `M not start view W destination is changed { no route }`(
        forge: Forge
    ) {
        // Given
        whenever(mockPredicate.accept(any())) doReturn false
        val arguments = Bundle()
        val mockNavDestination = forge.aNavDestination(routeValue = null)

        // When
        testedObserver.onDestinationChanged(mockNavController, mockNavDestination, arguments)

        // Then
        verifyNoInteractions(mockRumMonitor)
    }

    // endregion

    // region LifecycleEventObserver callbacks

    @Test
    fun `M add navigation observer W ON_RESUME event`() {
        // When
        testedObserver.onStateChanged(mock(), Lifecycle.Event.ON_RESUME)

        // Then
        verify(mockNavController).addOnDestinationChangedListener(testedObserver)
        verifyNoMoreInteractions(mockNavController)
    }

    @Test
    fun `M remove navigation observer & stop view W ON_PAUSE event`(
        forge: Forge
    ) {
        // Given
        val mockDestination = forge.aNavDestination()
        whenever(mockNavController.currentDestination) doReturn mockDestination

        // When
        testedObserver.onStateChanged(mock(), Lifecycle.Event.ON_PAUSE)

        // Then
        verify(mockNavController).removeOnDestinationChangedListener(testedObserver)
        verify(mockRumMonitor).stopView(
            mockDestination.route!!
        )
    }

    @Test
    fun `M only remove navigation observer W ON_PAUSE event { no current destination }`() {
        // Given
        whenever(mockNavController.currentDestination) doReturn null

        // When
        testedObserver.onStateChanged(mock(), Lifecycle.Event.ON_PAUSE)

        // Then
        verify(mockNavController).removeOnDestinationChangedListener(testedObserver)
        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `M only remove navigation observer W ON_PAUSE event { no route for destination }`(
        forge: Forge
    ) {
        // Given
        val mockDestination = forge.aNavDestination(routeValue = null)
        whenever(mockNavController.currentDestination) doReturn mockDestination

        // When
        testedObserver.onStateChanged(mock(), Lifecycle.Event.ON_PAUSE)

        // Then
        verify(mockNavController).removeOnDestinationChangedListener(testedObserver)
        verifyNoInteractions(mockRumMonitor)
    }

    @ParameterizedTest
    @EnumSource(
        Lifecycle.Event::class,
        names = ["ON_PAUSE", "ON_RESUME"],
        mode = EnumSource.Mode.EXCLUDE
    )
    fun `M do nothing W neither ON_PAUSE or ON_RESUME event`(
        lifecycleEvent: Lifecycle.Event
    ) {
        // When
        testedObserver.onStateChanged(mock(), lifecycleEvent)

        // Then
        verifyNoInteractions(mockPredicate, mockNavController, mockRumMonitor)
    }

    // endregion

    @Test
    fun `M remove observer W onDispose()`() {
        // When
        testedObserver.onDispose()

        // Then
        verify(mockNavController).removeOnDestinationChangedListener(testedObserver)
    }

    // region private

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
