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

    @Test
    fun `M track slot W notifySlotChanged { slot is set }`() {
        // Given
        val fakeRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)

        try {
            // When
            EmbeddedContentSlotRegistry.notifySlotChanged(null, fakeRegistration)

            // Then
            assertThat(EmbeddedContentSlotRegistry.hasMarkedSlots()).isTrue()
            assertThat(EmbeddedContentSlotRegistry.isSlotMarked(FAKE_SLOT_ID)).isTrue()
        } finally {
            EmbeddedContentSlotRegistry.notifySlotChanged(fakeRegistration, null)
        }
    }

    @Test
    fun `M stop tracking slot W notifySlotChanged { slot is cleared }`() {
        // Given
        val fakeRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)
        EmbeddedContentSlotRegistry.notifySlotChanged(null, fakeRegistration)

        // When
        EmbeddedContentSlotRegistry.notifySlotChanged(fakeRegistration, null)

        // Then
        assertThat(EmbeddedContentSlotRegistry.hasMarkedSlots()).isFalse()
        assertThat(EmbeddedContentSlotRegistry.isSlotMarked(FAKE_SLOT_ID)).isFalse()
    }

    @Test
    fun `M replace tracked slot W notifySlotChanged { registration changes }`() {
        // Given
        val fakeOldRegistration = EmbeddedContentSlotRegistration(FAKE_OLD_SLOT_ID)
        val fakeNewRegistration = EmbeddedContentSlotRegistration(FAKE_NEW_SLOT_ID)
        EmbeddedContentSlotRegistry.notifySlotChanged(null, fakeOldRegistration)

        try {
            // When
            EmbeddedContentSlotRegistry.notifySlotChanged(
                fakeOldRegistration,
                fakeNewRegistration
            )

            // Then
            assertThat(EmbeddedContentSlotRegistry.isSlotMarked(FAKE_OLD_SLOT_ID)).isFalse()
            assertThat(EmbeddedContentSlotRegistry.isSlotMarked(FAKE_NEW_SLOT_ID)).isTrue()
        } finally {
            EmbeddedContentSlotRegistry.notifySlotChanged(fakeNewRegistration, null)
        }
    }

    @Test
    fun `M retain replacement W notifySlotChanged { stale registration is cleared }`() {
        // Given
        val fakeStaleRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)
        val fakeCurrentRegistration = EmbeddedContentSlotRegistration(FAKE_SLOT_ID)
        EmbeddedContentSlotRegistry.notifySlotChanged(null, fakeStaleRegistration)
        EmbeddedContentSlotRegistry.notifySlotChanged(null, fakeCurrentRegistration)

        try {
            // When
            EmbeddedContentSlotRegistry.notifySlotChanged(fakeStaleRegistration, null)

            // Then
            assertThat(EmbeddedContentSlotRegistry.isSlotMarked(FAKE_SLOT_ID)).isTrue()
        } finally {
            EmbeddedContentSlotRegistry.notifySlotChanged(fakeCurrentRegistration, null)
        }
    }

    private companion object {
        const val FAKE_SLOT_ID = "slot-id"
        const val FAKE_OLD_SLOT_ID = "old-slot"
        const val FAKE_NEW_SLOT_ID = "new-slot"
    }
}
