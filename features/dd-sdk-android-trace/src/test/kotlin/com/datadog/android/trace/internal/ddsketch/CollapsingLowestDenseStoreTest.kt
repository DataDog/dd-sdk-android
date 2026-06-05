/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.ddsketch

import com.datadog.android.utils.forge.Configurator
import com.datadoghq.sketch.ddsketch.store.StoreProtoBinding
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import com.datadoghq.sketch.ddsketch.store.CollapsingLowestDenseStore as RefCollapsingLowestDenseStore

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CollapsingLowestDenseStoreTest {

    // region Empty store

    @Test
    fun `M be empty W isEmpty() {no values added}`() {
        // When / Then
        assertThat(newStore().isEmpty).isTrue()
    }

    @Test
    fun `M return zero W getTotalCount() {no values added}`() {
        // When / Then
        assertThat(newStore().totalCount).isZero()
    }

    @Test
    fun `M returns Integer MAX_VALUE W getMinIndex() {empty store}`() {
        // When / Then
        assertThat(newStore().minIndex).isEqualTo(Integer.MAX_VALUE)
    }

    @Test
    fun `M returns Integer MIN_VALUE W getMaxIndex() {empty store}`() {
        // When / Then
        assertThat(newStore().maxIndex).isEqualTo(Integer.MIN_VALUE)
    }

    // endregion

    // region Add

    @Test
    fun `M not be empty W isEmpty() {after adding a value}`(
        @IntForgery(min = -1000, max = 1000) fakeIndex: Int
    ) {
        // Given
        val store = newStore { add(fakeIndex) }

        // Then
        assertThat(store.isEmpty).isFalse()
    }

    // endregion

    // region Collapsing behavior

    @Test
    fun `M shift existing data right and collapse new low index W add() {new index below array left edge}`() {
        // Given: maxNumBins=3 → array length 3, offset=5 after first add
        val store = newStore(maxNumBins = 3) { add(5) }

        // When: add(1) → range [1..5]=5 > 3, recalculated newMinIndex=3, shift=offset(5)-newMinIndex(3)=2 > 0
        // Existing slot for index 5 slides right; index 1 collapses into the new minimum bin
        store.add(1)

        // Then
        assertThat(store.totalCount).isEqualTo(2.0)
        assertThat(store.minIndex).isEqualTo(3) // collapsed lower boundary — covers indices ≤ 3
        assertThat(store.maxIndex).isEqualTo(5) // high-index data preserved intact
    }

    @Test
    fun `M route to collapsed bin W add() {index below minIndex on already-collapsed store}`() {
        // Given: collapse the store so isCollapsed=true and minIndex=3
        val store = newStore(maxNumBins = 3) {
            add(5)
            add(1) // triggers collapse: minIndex becomes 3
        }

        // When: add below minIndex on an already-collapsed store — hits the isCollapsed fast-path
        store.add(2)

        // Then: count accumulates in the collapsed bin, minIndex unchanged
        assertThat(store.totalCount).isEqualTo(3.0)
        assertThat(store.minIndex).isEqualTo(3)
    }

    @Test
    fun `M collapse all into max bin W add() {maxNumBins is 1}`() {
        // Given
        val store = newStore(maxNumBins = 1) {
            add(0)
            add(1)
            add(2)
        }

        // Then
        assertThat(store.totalCount).isEqualTo(3.0)
        assertThat(store.minIndex).isEqualTo(store.maxIndex)
        assertThat(store.maxIndex).isEqualTo(2)
    }

    @Test
    fun `M preserve total count W add() {values exceed maxNumBins}`(
        @IntForgery(min = 1, max = 64) fakeMaxBins: Int,
        @IntForgery(min = 200, max = 1000) fakeNumValues: Int
    ) {
        // Given
        val store = newStore(maxNumBins = fakeMaxBins) {
            repeat(fakeNumValues) { add(it) }
        }

        // Then
        assertThat(store.totalCount).isEqualTo(fakeNumValues.toDouble())
    }

    @Test
    fun `M retain accurate maxIndex W add() {values exceed maxNumBins}`(
        @IntForgery(min = 1, max = 64) fakeMaxBins: Int,
        @IntForgery(min = 200, max = 1000) fakeNumValues: Int
    ) {
        // Given
        val store = newStore(maxNumBins = fakeMaxBins) {
            repeat(fakeNumValues) { add(it) }
        }

        // Then
        assertThat(store.maxIndex).isEqualTo(fakeNumValues - 1)
    }

    @Test
    fun `M satisfy minIndex invariant W add() {values exceed maxNumBins}`(
        @IntForgery(min = 1, max = 64) fakeMaxBins: Int,
        @IntForgery(min = 200, max = 1000) fakeNumValues: Int
    ) {
        // Given
        val store = newStore(maxNumBins = fakeMaxBins) {
            repeat(fakeNumValues) { add(it) }
        }

        // Then: minIndex must be >= maxIndex - maxNumBins + 1
        assertThat(store.minIndex).isGreaterThanOrEqualTo(store.maxIndex - fakeMaxBins + 1)
    }

    // endregion

    // region Extreme values

    @Test
    fun `M handle extreme indices W add() {Integer MIN and MAX value}`() {
        // Given
        val store = newStore {
            add(Int.MIN_VALUE)
            add(Int.MAX_VALUE)
        }

        // Then
        assertThat(store.totalCount).isEqualTo(2.0)
        assertThat(store.isEmpty).isFalse()
        assertThat(store.maxIndex).isEqualTo(Int.MAX_VALUE)
    }

    // endregion

    // region Serialization

    @Test
    fun `M return 0 W serializedSize() {empty store}`() {
        // When / Then
        assertThat(newStore().serializedSize()).isEqualTo(0)
    }

    @Test
    fun `M write exactly serializedSize bytes W writeTo() {non-empty store}`(
        @IntForgery(min = 1, max = 500) fakeNumValues: Int
    ) {
        // Given
        val store = newStore { repeat(fakeNumValues) { add(it) } }
        val serializer = DDSketchSerializer(store.serializedSize())

        // When
        store.writeTo(serializer)

        // Then
        assertThat(serializer.toByteArray().size).isEqualTo(store.serializedSize())
    }

    @Test
    fun `M produce identical bytes to reference proto encoding W writeTo() {empty store}`() {
        // Given
        val ourStore = newStore()
        val refStore = RefCollapsingLowestDenseStore(1_000)
        val serializer = DDSketchSerializer(0)

        // When
        ourStore.writeTo(serializer)
        val ourBytes = serializer.toByteArray()
        val refBytes = StoreProtoBinding.toProto(refStore).toByteArray()

        // Then
        assertThat(ourBytes).isEqualTo(refBytes)
    }

    @Test
    fun `M produce identical bytes to reference proto encoding W writeTo() {single index per bin}`(
        @IntForgery(min = 1, max = 50) fakeBinCount: Int
    ) {
        // Given: fakeBinCount distinct adjacent indices, one count each
        val ourStore = newStore { for (i in 0 until fakeBinCount) add(i) }
        val refStore = RefCollapsingLowestDenseStore(1_000).also { for (i in 0 until fakeBinCount) it.add(i) }
        val serializer = DDSketchSerializer(ourStore.serializedSize())

        // When
        ourStore.writeTo(serializer)
        val ourBytes = serializer.toByteArray()
        val refBytes = StoreProtoBinding.toProto(refStore).toByteArray()

        // Then
        assertThat(ourBytes).isEqualTo(refBytes)
    }

    @Test
    fun `M produce identical bytes to reference proto encoding W writeTo() {accumulated counts}`() {
        // Given: index 0 added twice, index 1 added once → counts = [2.0, 1.0]
        val ourStore = newStore { add(0); add(0); add(1) }
        val refStore = RefCollapsingLowestDenseStore(1_000).also { it.add(0); it.add(0); it.add(1) }
        val serializer = DDSketchSerializer(ourStore.serializedSize())

        // When
        ourStore.writeTo(serializer)
        val ourBytes = serializer.toByteArray()
        val refBytes = StoreProtoBinding.toProto(refStore).toByteArray()

        // Then
        assertThat(ourBytes).isEqualTo(refBytes)
    }

    // endregion

    // region Helpers

    private fun newStore(
        maxNumBins: Int = 1_000,
        init: CollapsingLowestDenseStore.() -> Unit = {}
    ) = CollapsingLowestDenseStore(maxNumBins).apply(init)

    // endregion
}
