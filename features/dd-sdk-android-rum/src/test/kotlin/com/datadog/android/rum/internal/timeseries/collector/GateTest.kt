/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.collector

import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness
import java.lang.reflect.Modifier

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class GateTest {

    private lateinit var testedGate: Gate

    @Forgery
    lateinit var fakeRumContext: RumContext

    private val mockOnUpdated: (Int, RumContext) -> Unit = mock()

    private fun fakeRumContextOf(viewType: RumViewType) = fakeRumContext.copy(viewType = viewType)

    @BeforeEach
    fun `set up`() {
        testedGate = Gate()
    }

    @Test
    fun `M declare foreground volatile W inspect field modifiers`() {
        // When
        val modifiers = Gate::class.java.getDeclaredField("foreground").modifiers

        // Then
        assertThat(Modifier.isVolatile(modifiers)).isTrue()
    }

    // region getRumContext() / runIfAllowed()

    @Test
    fun `M return null W getRumContext() { nothing allowed yet }`() {
        // When / Then
        assertThat(testedGate.getRumContext()).isNull()
    }

    @Test
    fun `M return context W getRumContext() { foreground, session active, context set }`() {
        // Given
        testedGate.setForeground(true)
        testedGate.setSessionActive(true) { _, _ -> }
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }

        // When / Then
        assertThat(testedGate.getRumContext()).isEqualTo(fakeRumContextOf(RumViewType.FOREGROUND))
    }

    @Test
    fun `M return null W getRumContext() { not foreground }`() {
        // Given
        testedGate.setSessionActive(true) { _, _ -> }
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }

        // When / Then
        assertThat(testedGate.getRumContext()).isNull()
    }

    @Test
    fun `M return latest context W getRumContext() { context replaced while allowed }`() {
        // Given
        val fakeNextContext = fakeRumContextOf(RumViewType.FOREGROUND).copy(viewId = "next")
        testedGate.setForeground(true)
        testedGate.setSessionActive(true) { _, _ -> }
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }

        // When
        testedGate.setRumContext(fakeNextContext) { _, _ -> }

        // Then
        assertThat(testedGate.getRumContext()).isEqualTo(fakeNextContext)
    }

    @Test
    fun `M invoke runnable W runIfAllowed() { generation matches, allowed }`() {
        // Given
        testedGate.setForeground(true)
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }
        var generation = -1
        testedGate.setSessionActive(true) { newGeneration, _ -> generation = newGeneration }

        // When
        var received: RumContext? = null
        val executed = testedGate.runIfGateOpened(generation) { received = it }

        // Then
        assertThat(executed).isTrue()
        assertThat(received).isEqualTo(fakeRumContextOf(RumViewType.FOREGROUND))
    }

    @Test
    fun `M not invoke runnable W runIfAllowed() { stale generation }`() {
        // Given
        testedGate.setForeground(true)
        var generation = 0
        testedGate.setSessionActive(true) { newGeneration, _ -> generation = newGeneration }
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }
        val staleGeneration = generation
        testedGate.setForeground(false) { _, _ -> }

        // When
        var invoked = false
        val executed = testedGate.runIfGateOpened(staleGeneration) { invoked = true }

        // Then
        assertThat(executed).isFalse()
        assertThat(invoked).isFalse()
    }

    @Test
    fun `M not invoke runnable W runIfAllowed() { not allowed }`() {
        // When
        var invoked = false
        val executed = testedGate.runIfGateOpened(0) { invoked = true }

        // Then
        assertThat(executed).isFalse()
        assertThat(invoked).isFalse()
    }

    // endregion

    // region setSessionActive()

    @Test
    fun `M not notify W setSessionActive() { app not resumed }`() {
        // Given
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }

        // When
        testedGate.setSessionActive(true, mockOnUpdated)

        // Then
        verifyNoInteractions(mockOnUpdated)
    }

    @Test
    fun `M not notify W setSessionActive() { no foreground context recorded yet }`() {
        // Given
        testedGate.setForeground(true)

        // When
        testedGate.setSessionActive(true, mockOnUpdated)

        // Then
        verifyNoInteractions(mockOnUpdated)
    }

    @Test
    fun `M notify W setSessionActive() { becomes the last condition satisfied }`() {
        // Given
        testedGate.setForeground(true)
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }

        // When
        testedGate.setSessionActive(true, mockOnUpdated)

        // Then
        verify(mockOnUpdated).invoke(2, fakeRumContextOf(RumViewType.FOREGROUND))
    }

    @Test
    fun `M notify once W setSessionActive() { transitions from allowed to not allowed }`() {
        // Given
        testedGate.setForeground(true)
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }
        testedGate.setSessionActive(true) { _, _ -> }

        // When
        testedGate.setSessionActive(false, mockOnUpdated)

        // Then
        verify(mockOnUpdated).invoke(3, fakeRumContextOf(RumViewType.FOREGROUND))
    }

    @Test
    fun `M not notify W setSessionActive() { called again with the same value }`() {
        // Given
        testedGate.setForeground(true)
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }
        testedGate.setSessionActive(true) { _, _ -> }

        // When
        testedGate.setSessionActive(true, mockOnUpdated)

        // Then
        verifyNoInteractions(mockOnUpdated)
    }

    @Test
    fun `M not notify twice W setSessionActive(false) { called twice }`() {
        // Given
        testedGate.setForeground(true)
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }
        testedGate.setSessionActive(true) { _, _ -> }
        testedGate.setSessionActive(false, mockOnUpdated)

        // When
        testedGate.setSessionActive(false, mockOnUpdated)

        // Then
        verify(mockOnUpdated, times(1)).invoke(any(), any())
    }

    // endregion

    // region setForeground()

    @Test
    fun `M not notify W setForeground() { session not active }`() {
        // Given
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }

        // When
        testedGate.setForeground(true, mockOnUpdated)

        // Then
        verifyNoInteractions(mockOnUpdated)
    }

    @Test
    fun `M notify W setForeground() { session already active, no context yet, context arrives first }`() {
        // Given
        testedGate.setSessionActive(true) { _, _ -> }
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }

        // When
        testedGate.setForeground(true, mockOnUpdated)

        // Then
        verify(mockOnUpdated).invoke(2, fakeRumContextOf(RumViewType.FOREGROUND))
    }

    @Test
    fun `M not notify W setForeground() { called again with the same value }`() {
        // Given
        testedGate.setSessionActive(true) { _, _ -> }
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }
        testedGate.setForeground(true) { _, _ -> }

        // When
        testedGate.setForeground(true, mockOnUpdated)

        // Then
        verifyNoInteractions(mockOnUpdated)
    }

    // endregion

    // region setRumContext()

    @Test
    fun `M not notify W setRumContext() { session not started, app not resumed }`() {
        // When
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND), mockOnUpdated)

        // Then
        verifyNoInteractions(mockOnUpdated)
    }

    @ParameterizedTest
    @EnumSource(value = RumViewType::class, names = ["BACKGROUND", "NONE"])
    fun `M accept context W setRumContext() { view type is not foreground }`(viewType: RumViewType) {
        // Given
        val fakeContext = fakeRumContextOf(viewType)
        testedGate.setForeground(true)
        testedGate.setSessionActive(true) { _, _ -> }

        // When
        testedGate.setRumContext(fakeContext, mockOnUpdated)

        // Then
        assertThat(testedGate.getRumContext()).isEqualTo(fakeContext)
        verify(mockOnUpdated).invoke(2, fakeContext)
    }

    @Test
    fun `M not notify W setRumContext() { background context replaces foreground while allowed }`() {
        // Given
        val fakeNextContext = fakeRumContextOf(RumViewType.BACKGROUND).copy(viewId = "next")
        testedGate.setForeground(true)
        testedGate.setSessionActive(true) { _, _ -> }
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }

        // When
        testedGate.setRumContext(fakeNextContext, mockOnUpdated)

        // Then
        assertThat(testedGate.getRumContext()).isEqualTo(fakeNextContext)
        verifyNoInteractions(mockOnUpdated)
    }

    @Test
    fun `M not notify W setRumContext() { same context again }`() {
        // Given
        testedGate.setForeground(true)
        testedGate.setSessionActive(true) { _, _ -> }
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }

        // When
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND), mockOnUpdated)

        // Then
        verifyNoInteractions(mockOnUpdated)
    }

    @Test
    fun `M notify W setRumContext() { becomes the last condition satisfied }`() {
        // Given
        testedGate.setForeground(true)
        testedGate.setSessionActive(true) { _, _ -> }

        // When
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND), mockOnUpdated)

        // Then
        verify(mockOnUpdated).invoke(2, fakeRumContextOf(RumViewType.FOREGROUND))
    }

    @Test
    fun `M remove cached context W setSessionActive() { becomes inactive }`() {
        // Given
        testedGate.setForeground(true)
        testedGate.setSessionActive(true) { _, _ -> }
        testedGate.setRumContext(fakeRumContextOf(RumViewType.FOREGROUND)) { _, _ -> }

        // When
        testedGate.setSessionActive(false) { _, _ -> }

        // Then
        assertThat(testedGate.getRumContext()).isNull()
    }

    // endregion
}
