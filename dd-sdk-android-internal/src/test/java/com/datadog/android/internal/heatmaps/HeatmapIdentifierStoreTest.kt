/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.heatmaps

import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class HeatmapIdentifierStoreTest {

    private lateinit var testedStore: HeatmapIdentifierStore

    @BeforeEach
    fun `set up`() {
        testedStore = HeatmapIdentifierStore()
    }

    @Test
    fun `M return null W getHeatmapIdentifier { store not initialized }`(
        @LongForgery fakeViewId: Long,
        @StringForgery fakeScreenName: String
    ) {
        assertThat(testedStore.getHeatmapIdentifier(fakeViewId, fakeScreenName)).isNull()
    }

    @Test
    fun `M replace snapshot W setHeatmapIdentifiers { called twice }`(forge: Forge) {
        // Given
        val fakeScreenName = forge.anAlphabeticalString()
        val firstViewId = forge.aLong()
        val secondViewId = forge.aLong(min = firstViewId + 1L)
        val firstIdentifier = HeatmapIdentifier(forge.anAlphabeticalString())
        val secondIdentifier = HeatmapIdentifier(forge.anAlphabeticalString())
        testedStore.setHeatmapIdentifiers(mapOf(firstViewId to firstIdentifier), fakeScreenName)

        // When
        testedStore.setHeatmapIdentifiers(mapOf(secondViewId to secondIdentifier), fakeScreenName)

        // Then
        assertThat(testedStore.getHeatmapIdentifier(firstViewId, fakeScreenName)).isNull()
        assertThat(testedStore.getHeatmapIdentifier(secondViewId, fakeScreenName)).isEqualTo(secondIdentifier)
    }

    @Test
    fun `M return null W heatmapIdentifier { snapshot replaced with empty }`(
        @LongForgery fakeViewId: Long,
        @StringForgery fakeRawValue: String,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        testedStore.setHeatmapIdentifiers(mapOf(fakeViewId to HeatmapIdentifier(fakeRawValue)), fakeScreenName)

        // When
        testedStore.setHeatmapIdentifiers(emptyMap(), fakeScreenName)

        // Then
        assertThat(testedStore.getHeatmapIdentifier(fakeViewId, fakeScreenName)).isNull()
    }

    @Test
    fun `M return null W heatmapIdentifier { screen name mismatch }`(
        @LongForgery fakeViewId: Long,
        @StringForgery fakeRawValue: String,
        @StringForgery fakeSnapshotScreenName: String
    ) {
        // Given
        val fakeCurrentScreenName = fakeSnapshotScreenName + "_different"
        testedStore.setHeatmapIdentifiers(
            mapOf(fakeViewId to HeatmapIdentifier(fakeRawValue)),
            fakeSnapshotScreenName
        )

        // When - RUM reads with a different screen name (stale snapshot)
        val result = testedStore.getHeatmapIdentifier(fakeViewId, fakeCurrentScreenName)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return identifier W heatmapIdentifier { screen name matches }`(
        @LongForgery fakeViewId: Long,
        @StringForgery fakeRawValue: String,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        val fakeIdentifier = HeatmapIdentifier(fakeRawValue)
        testedStore.setHeatmapIdentifiers(mapOf(fakeViewId to fakeIdentifier), fakeScreenName)

        // When
        val result = testedStore.getHeatmapIdentifier(fakeViewId, fakeScreenName)

        // Then
        assertThat(result).isEqualTo(fakeIdentifier)
    }

    // region factory

    @Test
    fun `M return functional registry W HeatmapIdentifierRegistry create()`(
        @LongForgery fakeViewId: Long,
        @StringForgery fakeRawValue: String,
        @StringForgery fakeScreenName: String
    ) {
        // Given
        val testedRegistry = HeatmapIdentifierRegistry.create()
        val fakeIdentifier = HeatmapIdentifier(fakeRawValue)

        // When
        testedRegistry.setHeatmapIdentifiers(mapOf(fakeViewId to fakeIdentifier), fakeScreenName)
        val result = testedRegistry.getHeatmapIdentifier(fakeViewId, fakeScreenName)

        // Then
        assertThat(result).isEqualTo(fakeIdentifier)
    }

    // endregion

    // region thread safety

    @Test
    fun `M not throw W concurrent setHeatmapIdentifiers { multiple writers }`(forge: Forge) {
        // Given
        val fakeScreenName = forge.anAlphabeticalString()
        val viewIdA = forge.aLong()
        val viewIdB = forge.aLong(min = viewIdA + 1L)
        val identifierA = HeatmapIdentifier(forge.anAlphabeticalString())
        val identifierB = HeatmapIdentifier(forge.anAlphabeticalString())
        val iterations = forge.anInt(min = 50, max = 200)
        val firstError = AtomicReference<Throwable?>(null)

        // When
        val threads = listOf(
            Thread {
                try {
                    repeat(iterations) {
                        testedStore.setHeatmapIdentifiers(mapOf(viewIdA to identifierA), fakeScreenName)
                    }
                } catch (t: Throwable) {
                    firstError.compareAndSet(null, t)
                }
            }.apply { start() },
            Thread {
                try {
                    repeat(iterations) {
                        testedStore.setHeatmapIdentifiers(mapOf(viewIdB to identifierB), fakeScreenName)
                    }
                } catch (t: Throwable) {
                    firstError.compareAndSet(null, t)
                }
            }.apply { start() }
        )
        threads.forEach { it.join() }

        // Then
        assertThat(firstError.get()).isNull()
        // Atomic replacement: exactly one snapshot survives, never a merged state.
        val resultA = testedStore.getHeatmapIdentifier(viewIdA, fakeScreenName)
        val resultB = testedStore.getHeatmapIdentifier(viewIdB, fakeScreenName)
        val finalIsAOnly = resultA == identifierA && resultB == null
        val finalIsBOnly = resultA == null && resultB == identifierB
        assertThat(finalIsAOnly || finalIsBOnly).isTrue()
    }

    @Test
    fun `M not throw W concurrent setHeatmapIdentifiers and heatmapIdentifier`(forge: Forge) {
        // Given
        val fakeScreenName = forge.anAlphabeticalString()
        val viewId = forge.aLong()
        val identifierA = HeatmapIdentifier(forge.anAlphabeticalString())
        val identifierB = HeatmapIdentifier(forge.anAlphabeticalString())
        val iterations = forge.anInt(min = 50, max = 200)
        val firstError = AtomicReference<Throwable?>(null)

        // When
        val writer = Thread {
            try {
                repeat(iterations) { i ->
                    val identifier = if (i % 2 == 0) identifierA else identifierB
                    testedStore.setHeatmapIdentifiers(mapOf(viewId to identifier), fakeScreenName)
                }
            } catch (t: Throwable) {
                firstError.compareAndSet(null, t)
            }
        }.apply { start() }
        val reader = Thread {
            try {
                repeat(iterations) {
                    testedStore.getHeatmapIdentifier(viewId, fakeScreenName)
                }
            } catch (t: Throwable) {
                firstError.compareAndSet(null, t)
            }
        }.apply { start() }
        listOf(writer, reader).forEach { it.join() }

        // Then
        assertThat(firstError.get()).isNull()
        // Final value is one of the published identifiers, never torn or stale.
        val finalResult = testedStore.getHeatmapIdentifier(viewId, fakeScreenName)
        assertThat(finalResult).isIn(identifierA, identifierB)
    }

    @Test
    fun `M return only published values W sustained many-thread race`(forge: Forge) {
        // Given
        val fakeScreenName = forge.anAlphabeticalString()
        val viewId = forge.aLong()
        val publishedIdentifiers = forge.aList(size = NUM_DISTINCT_IDENTIFIERS) {
            HeatmapIdentifier(forge.anAlphabeticalString())
        }
        val publishedSet = publishedIdentifiers.toSet()
        val seenValues = ConcurrentLinkedQueue<HeatmapIdentifier>()
        val firstError = AtomicReference<Throwable?>(null)

        // When
        // Identifiers are selected by cycling through the pre-built list using the iteration
        // index — forge must not be called from threads because Forge is not thread-safe.
        val writers = (0 until NUM_WRITER_THREADS).map {
            Thread {
                try {
                    repeat(WRITES_PER_WRITER) { i ->
                        val identifier = publishedIdentifiers[i % publishedIdentifiers.size]
                        testedStore.setHeatmapIdentifiers(mapOf(viewId to identifier), fakeScreenName)
                    }
                } catch (t: Throwable) {
                    firstError.compareAndSet(null, t)
                }
            }.apply { start() }
        }
        val readers = (0 until NUM_READER_THREADS).map {
            Thread {
                try {
                    repeat(READS_PER_READER) {
                        // ConcurrentLinkedQueue.add() doesn't throw for non-null elements
                        @Suppress("UnsafeThirdPartyFunctionCall")
                        testedStore.getHeatmapIdentifier(viewId, fakeScreenName)?.let { seenValues.add(it) }
                    }
                } catch (t: Throwable) {
                    firstError.compareAndSet(null, t)
                }
            }.apply { start() }
        }
        (writers + readers).forEach { it.join() }

        // Then
        assertThat(firstError.get()).isNull()
        // Every value the readers ever observed must be one we actually published — never a torn,
        // half-built, or fabricated identifier.
        assertThat(seenValues).allSatisfy { assertThat(it).isIn(publishedSet) }
    }

    // endregion

    companion object {
        private const val NUM_DISTINCT_IDENTIFIERS = 50
        private const val NUM_WRITER_THREADS = 4
        private const val NUM_READER_THREADS = 4
        private const val WRITES_PER_WRITER = 500
        private const val READS_PER_READER = 2000
    }
}
