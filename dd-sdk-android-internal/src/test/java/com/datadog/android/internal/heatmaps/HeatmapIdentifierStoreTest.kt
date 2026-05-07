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
    fun `M replace snapshot W setHeatmapIdentifiers { called twice }`(forge: Forge) {
        // Given
        val firstViewId = forge.aLong()
        val secondViewId = forge.aLong(min = firstViewId + 1L)
        val firstIdentifier = HeatmapIdentifier(forge.anAlphabeticalString())
        val secondIdentifier = HeatmapIdentifier(forge.anAlphabeticalString())
        testedStore.setHeatmapIdentifiers(mapOf(firstViewId to firstIdentifier))

        // When
        testedStore.setHeatmapIdentifiers(mapOf(secondViewId to secondIdentifier))

        // Then
        assertThat(testedStore.heatmapIdentifier(firstViewId)).isNull()
        assertThat(testedStore.heatmapIdentifier(secondViewId)).isEqualTo(secondIdentifier)
    }

    @Test
    fun `M return null W heatmapIdentifier { snapshot replaced with empty }`(
        @LongForgery fakeViewId: Long,
        @StringForgery fakeRawValue: String
    ) {
        // Given
        testedStore.setHeatmapIdentifiers(mapOf(fakeViewId to HeatmapIdentifier(fakeRawValue)))

        // When
        testedStore.setHeatmapIdentifiers(emptyMap())

        // Then
        assertThat(testedStore.heatmapIdentifier(fakeViewId)).isNull()
    }

    // region factory

    @Test
    fun `M return functional registry W HeatmapIdentifierRegistry create()`(
        @LongForgery fakeViewId: Long,
        @StringForgery fakeRawValue: String
    ) {
        // Given
        val testedRegistry = HeatmapIdentifierRegistry.create()
        val fakeIdentifier = HeatmapIdentifier(fakeRawValue)

        // When
        testedRegistry.setHeatmapIdentifiers(mapOf(fakeViewId to fakeIdentifier))
        val result = testedRegistry.heatmapIdentifier(fakeViewId)

        // Then
        assertThat(result).isEqualTo(fakeIdentifier)
    }

    // endregion

    // region thread safety

    @Test
    fun `M not throw W concurrent setHeatmapIdentifiers { multiple writers }`(forge: Forge) {
        // Given
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
                        testedStore.setHeatmapIdentifiers(mapOf(viewIdA to identifierA))
                    }
                } catch (t: Throwable) {
                    firstError.compareAndSet(null, t)
                }
            }.apply { start() },
            Thread {
                try {
                    repeat(iterations) {
                        testedStore.setHeatmapIdentifiers(mapOf(viewIdB to identifierB))
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
        val resultA = testedStore.heatmapIdentifier(viewIdA)
        val resultB = testedStore.heatmapIdentifier(viewIdB)
        val finalIsAOnly = resultA == identifierA && resultB == null
        val finalIsBOnly = resultA == null && resultB == identifierB
        assertThat(finalIsAOnly || finalIsBOnly).isTrue()
    }

    @Test
    fun `M not throw W concurrent setHeatmapIdentifiers and heatmapIdentifier`(forge: Forge) {
        // Given
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
                    testedStore.setHeatmapIdentifiers(mapOf(viewId to identifier))
                }
            } catch (t: Throwable) {
                firstError.compareAndSet(null, t)
            }
        }.apply { start() }
        val reader = Thread {
            try {
                repeat(iterations) {
                    testedStore.heatmapIdentifier(viewId)
                }
            } catch (t: Throwable) {
                firstError.compareAndSet(null, t)
            }
        }.apply { start() }
        listOf(writer, reader).forEach { it.join() }

        // Then
        assertThat(firstError.get()).isNull()
        // Final value is one of the published identifiers, never torn or stale.
        val finalResult = testedStore.heatmapIdentifier(viewId)
        assertThat(finalResult).isIn(identifierA, identifierB)
    }

    @Test
    fun `M return only published values W sustained many-thread race`(forge: Forge) {
        // Given
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
                        testedStore.setHeatmapIdentifiers(mapOf(viewId to identifier))
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
                        testedStore.heatmapIdentifier(viewId)?.let { seenValues.add(it) }
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
