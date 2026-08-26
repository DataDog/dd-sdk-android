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

    // region placeholders

    @Test
    fun `M report placeholder W onPlaceholdersWritten`() {
        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // Then
        assertThat(testedRegistry.placeholder(FAKE_SLOT_ID))
            .isEqualTo(EmbeddedContentSlotRegistry.Placeholder(FAKE_VIEW_ID, FAKE_TIMESTAMP))
    }

    @Test
    fun `M report no placeholder W placeholder { slot never drawn }`() {
        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // Then
        assertThat(testedRegistry.placeholder(FAKE_OLD_SLOT_ID)).isNull()
    }

    @Test
    fun `M keep first timestamp W onPlaceholdersWritten { drawn again in same view }`() {
        // Given
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_LATER_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // Then
        assertThat(testedRegistry.placeholder(FAKE_SLOT_ID)?.timestamp).isEqualTo(FAKE_TIMESTAMP)
    }

    @Test
    fun `M replace placeholder W onPlaceholdersWritten { drawn in a new view }`() {
        // Given
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // When
        testedRegistry.onPlaceholdersWritten(FAKE_NEW_VIEW_ID, FAKE_LATER_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // Then
        assertThat(testedRegistry.placeholder(FAKE_SLOT_ID))
            .isEqualTo(EmbeddedContentSlotRegistry.Placeholder(FAKE_NEW_VIEW_ID, FAKE_LATER_TIMESTAMP))
    }

    @Test
    fun `M drop placeholder W onPlaceholdersWritten { slot no longer drawn }`() {
        // Given
        testedRegistry.onPlaceholdersWritten(
            FAKE_VIEW_ID,
            FAKE_TIMESTAMP,
            setOf(FAKE_SLOT_ID, FAKE_OLD_SLOT_ID)
        )

        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_LATER_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // Then
        assertThat(testedRegistry.placeholder(FAKE_OLD_SLOT_ID)).isNull()
        assertThat(testedRegistry.placeholder(FAKE_SLOT_ID)?.timestamp).isEqualTo(FAKE_TIMESTAMP)
    }

    @Test
    fun `M drop every placeholder W onPlaceholdersWritten { nothing drawn }`() {
        // Given
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_LATER_TIMESTAMP, emptySet())

        // Then
        assertThat(testedRegistry.placeholder(FAKE_SLOT_ID)).isNull()
    }

    @Test
    fun `M notify listener W onPlaceholdersWritten { first placeholder in view }`() {
        // Given
        val notified = mutableListOf<String>()
        testedRegistry.addPlaceholderListener { notified.add(it) }

        // When
        testedRegistry.onPlaceholdersWritten(
            FAKE_VIEW_ID,
            FAKE_TIMESTAMP,
            setOf(FAKE_SLOT_ID, FAKE_OLD_SLOT_ID)
        )

        // Then
        assertThat(notified).containsExactlyInAnyOrder(FAKE_SLOT_ID, FAKE_OLD_SLOT_ID)
    }

    @Test
    fun `M not notify listener W onPlaceholdersWritten { drawn again in same view }`() {
        // Given
        val notified = mutableListOf<String>()
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))
        testedRegistry.addPlaceholderListener { notified.add(it) }

        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_LATER_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // Then
        assertThat(notified).isEmpty()
    }

    @Test
    fun `M notify listener W onPlaceholdersWritten { slot drawn again after being dropped }`() {
        // Given
        val notified = mutableListOf<String>()
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_LATER_TIMESTAMP, emptySet())
        testedRegistry.addPlaceholderListener { notified.add(it) }

        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_LATEST_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // Then
        assertThat(notified).containsExactly(FAKE_SLOT_ID)
        assertThat(testedRegistry.placeholder(FAKE_SLOT_ID)?.timestamp).isEqualTo(FAKE_LATEST_TIMESTAMP)
    }

    @Test
    fun `M notify every listener W onPlaceholdersWritten { several listeners }`() {
        // Given
        val firstNotified = mutableListOf<String>()
        val secondNotified = mutableListOf<String>()
        testedRegistry.addPlaceholderListener { firstNotified.add(it) }
        testedRegistry.addPlaceholderListener { secondNotified.add(it) }

        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // Then
        assertThat(firstNotified).containsExactly(FAKE_SLOT_ID)
        assertThat(secondNotified).containsExactly(FAKE_SLOT_ID)
    }

    @Test
    fun `M notify snapshot listener W onPlaceholdersWritten { nothing newly placed }`() {
        // Given
        // A listener waiting on a slot learns from the snapshots that leave it out, so every
        // snapshot is reported, not only the ones that place something new.
        val notified = mutableListOf<Set<String>>()
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))
        testedRegistry.addSnapshotListener { notified.add(it) }

        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_LATER_TIMESTAMP, setOf(FAKE_SLOT_ID))
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_LATEST_TIMESTAMP, emptySet())

        // Then
        assertThat(notified).containsExactly(setOf(FAKE_SLOT_ID), emptySet())
    }

    @Test
    fun `M notify every snapshot listener W onPlaceholdersWritten { several listeners }`() {
        // Given
        val firstNotified = mutableListOf<Set<String>>()
        val secondNotified = mutableListOf<Set<String>>()
        testedRegistry.addSnapshotListener { firstNotified.add(it) }
        testedRegistry.addSnapshotListener { secondNotified.add(it) }

        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // Then
        assertThat(firstNotified).containsExactly(setOf(FAKE_SLOT_ID))
        assertThat(secondNotified).containsExactly(setOf(FAKE_SLOT_ID))
    }

    @Test
    fun `M keep placeholders isolated W separate instances`() {
        // Given
        val otherRegistry = EmbeddedContentSlotRegistry()

        // When
        testedRegistry.onPlaceholdersWritten(FAKE_VIEW_ID, FAKE_TIMESTAMP, setOf(FAKE_SLOT_ID))

        // Then
        assertThat(otherRegistry.placeholder(FAKE_SLOT_ID)).isNull()
    }

    // endregion

    private companion object {
        const val FAKE_SLOT_ID = "slot-id"
        const val FAKE_OLD_SLOT_ID = "old-slot"
        const val FAKE_NEW_SLOT_ID = "new-slot"
        const val FAKE_VIEW_ID = "view-id"
        const val FAKE_NEW_VIEW_ID = "new-view-id"
        const val FAKE_TIMESTAMP = 1_000L
        const val FAKE_LATER_TIMESTAMP = 2_000L
        const val FAKE_LATEST_TIMESTAMP = 3_000L
    }
}
