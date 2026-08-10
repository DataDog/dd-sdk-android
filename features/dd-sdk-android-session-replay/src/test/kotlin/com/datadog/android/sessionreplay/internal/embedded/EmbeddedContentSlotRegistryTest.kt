/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.embedded

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EmbeddedContentSlotRegistryTest {

    private val testedRegistry = EmbeddedContentSlotRegistry()

    @Test
    fun `M keep registry state isolated W separate instances`() {
        // Given
        val otherRegistry = EmbeddedContentSlotRegistry()
        val fakeRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)

        // When
        testedRegistry.notifySlotChanged(null, fakeRegistration)

        // Then
        assertThat(testedRegistry.hasMarkedSlots()).isTrue()
        assertThat(otherRegistry.hasMarkedSlots()).isFalse()
    }

    @Test
    fun `M track active registration W track`() {
        // Given
        val fakeRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)

        // When
        testedRegistry.track(fakeRegistration)

        // Then
        assertThat(testedRegistry.isSlotMarked(FAKE_SLOT_ID)).isTrue()
    }

    @Test
    fun `M return active slots W activeSlotIds`() {
        // Given
        val fakeActiveRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)
        val fakeInactiveRegistration = EmbeddedContentSlotRegistration(FAKE_OLD_SLOT_ID)
        testedRegistry.track(fakeActiveRegistration)
        testedRegistry.track(fakeInactiveRegistration)
        fakeInactiveRegistration.deactivate()

        // When
        val activeSlotIds = testedRegistry.activeSlotIds()

        // Then
        assertThat(activeSlotIds).containsExactly(FAKE_SLOT_ID)
    }

    @Test
    fun `M track slot W notifySlotChanged { slot is set }`() {
        // Given
        val fakeRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)

        try {
            // When
            testedRegistry.notifySlotChanged(null, fakeRegistration)

            // Then
            assertThat(testedRegistry.hasMarkedSlots()).isTrue()
            assertThat(testedRegistry.isSlotMarked(FAKE_SLOT_ID)).isTrue()
        } finally {
            testedRegistry.notifySlotChanged(fakeRegistration, null)
        }
    }

    @Test
    fun `M stop tracking slot W notifySlotChanged { slot is cleared }`() {
        // Given
        val fakeRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)
        testedRegistry.notifySlotChanged(null, fakeRegistration)

        // When
        testedRegistry.notifySlotChanged(fakeRegistration, null)

        // Then
        assertThat(testedRegistry.hasMarkedSlots()).isFalse()
        assertThat(testedRegistry.isSlotMarked(FAKE_SLOT_ID)).isFalse()
    }

    @Test
    fun `M replace tracked slot W notifySlotChanged { registration changes }`() {
        // Given
        val fakeOldRegistration = EmbeddedContentSlotRegistration(FAKE_OLD_SLOT_ID)
        val fakeNewRegistration = EmbeddedContentSlotRegistration(FAKE_NEW_SLOT_ID)
        testedRegistry.notifySlotChanged(null, fakeOldRegistration)

        try {
            // When
            testedRegistry.notifySlotChanged(
                fakeOldRegistration,
                fakeNewRegistration
            )

            // Then
            assertThat(testedRegistry.isSlotMarked(FAKE_OLD_SLOT_ID)).isFalse()
            assertThat(testedRegistry.isSlotMarked(FAKE_NEW_SLOT_ID)).isTrue()
        } finally {
            testedRegistry.notifySlotChanged(fakeNewRegistration, null)
        }
    }

    @Test
    fun `M retain replacement W notifySlotChanged { stale registration is cleared }`() {
        // Given
        val fakeStaleRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)
        val fakeCurrentRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)
        testedRegistry.notifySlotChanged(null, fakeStaleRegistration)
        testedRegistry.notifySlotChanged(null, fakeCurrentRegistration)

        try {
            // When
            testedRegistry.notifySlotChanged(fakeStaleRegistration, null)

            // Then
            assertThat(testedRegistry.isSlotMarked(FAKE_SLOT_ID)).isTrue()
        } finally {
            testedRegistry.notifySlotChanged(fakeCurrentRegistration, null)
        }
    }

    private companion object {
        const val FAKE_SLOT_ID = "slot-id"
        const val FAKE_OLD_SLOT_ID = "old-slot"
        const val FAKE_NEW_SLOT_ID = "new-slot"
    }
}
