/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.util.UUID

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class CollectedTimeseriesSessionRegistryTest {

    private lateinit var testedRegistry: CollectedTimeseriesSessionRegistry

    @BeforeEach
    fun `set up`() {
        testedRegistry = CollectedTimeseriesSessionRegistry()
    }

    @Test
    fun `M return false W isTimeseriesCollected() { nothing collected }`(
        @Forgery fakeSessionId: UUID
    ) {
        // When
        val result = testedRegistry.isTimeseriesCollected(fakeSessionId.toString())

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W isTimeseriesCollected() { collected for session }`(
        @Forgery fakeSessionId: UUID
    ) {
        // Given
        testedRegistry.setTimeseriesCollected(fakeSessionId.toString())

        // When
        val result = testedRegistry.isTimeseriesCollected(fakeSessionId.toString())

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W isTimeseriesCollected() { collected for other session only }`(
        @Forgery fakeOldSessionId: UUID,
        @Forgery fakeNewSessionId: UUID
    ) {
        // Given
        testedRegistry.setTimeseriesCollected(fakeOldSessionId.toString())

        // When
        val result = testedRegistry.isTimeseriesCollected(fakeNewSessionId.toString())

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M evict least recently used sessions W setTimeseriesCollected() { more than max sessions }`(
        forge: Forge
    ) {
        // Given
        val sessionIds = forge.fakeSessionIds()

        // When
        sessionIds.forEach { testedRegistry.setTimeseriesCollected(it) }

        // Then
        assertThat(sessionIds.dropLast(MAX_SESSIONS)).noneMatch { testedRegistry.isTimeseriesCollected(it) }
        assertThat(sessionIds.takeLast(MAX_SESSIONS)).allMatch { testedRegistry.isTimeseriesCollected(it) }
    }

    @Test
    fun `M not evict other sessions W setTimeseriesCollected() { same session set repeatedly }`(
        @Forgery fakeOldSessionId: UUID,
        @Forgery fakeNewSessionId: UUID
    ) {
        // Given
        testedRegistry.setTimeseriesCollected(fakeOldSessionId.toString())

        // When
        repeat(MAX_SESSIONS + 1) { testedRegistry.setTimeseriesCollected(fakeNewSessionId.toString()) }

        // Then
        assertThat(testedRegistry.isTimeseriesCollected(fakeOldSessionId.toString())).isTrue()
        assertThat(testedRegistry.isTimeseriesCollected(fakeNewSessionId.toString())).isTrue()
    }

    @Test
    fun `M keep recently read session W setTimeseriesCollected() { more than max sessions }`(
        @Forgery fakeActiveSessionId: UUID,
        forge: Forge
    ) {
        // Given
        val activeSessionId = fakeActiveSessionId.toString()
        testedRegistry.setTimeseriesCollected(activeSessionId)

        // When
        forge.fakeSessionIds().forEach {
            testedRegistry.isTimeseriesCollected(activeSessionId)
            testedRegistry.setTimeseriesCollected(it)
        }

        // Then
        assertThat(testedRegistry.isTimeseriesCollected(activeSessionId)).isTrue()
    }

    private fun Forge.fakeSessionIds(): List<String> =
        aList(anInt(min = MAX_SESSIONS + 1, max = MAX_SESSIONS * 4)) { getForgery<UUID>().toString() }.distinct()

    private companion object {
        const val MAX_SESSIONS = 3
    }
}
