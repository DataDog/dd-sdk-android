/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.ddsketch

import androidx.annotation.VisibleForTesting

/**
 * A quantile sketch with relative error guarantees. For a given `relativeAccuracy` r and a true
 * quantile value v, the reported value is guaranteed to lie within `[v*(1-r), v*(1+r)]`.
 *
 * For example, with `relativeAccuracy = 0.01`, a reported value of 1000 is guaranteed to be
 * between 990 and 1010.
 *
 * Values are mapped to bins via [LogarithmicMapping] and stored in two [CollapsingLowestDenseStore]
 * instances — one for positive values and one for negative values (stored as their positive
 * opposites). When either store exceeds `maxNumBins`, the lowest bins are collapsed, trading
 * precision at the low end of each store's range for bounded memory. Zero values are tracked
 * separately and are not affected by collapsing.
 *
 * Not thread-safe.
 */
internal class DDSketch(
    private val indexMapping: LogarithmicMapping,
    private val negativeValueStore: CollapsingLowestDenseStore,
    private val positiveValueStore: CollapsingLowestDenseStore
) {
    private var zeroCount = 0.0

    constructor(relativeAccuracy: Double, maxNumBins: Int) : this(
        LogarithmicMapping(relativeAccuracy),
        CollapsingLowestDenseStore(maxNumBins),
        CollapsingLowestDenseStore(maxNumBins)
    )

    fun add(value: Double) {
        if (value < -indexMapping.maxIndexedValue || value > indexMapping.maxIndexedValue) {
            // The input value is outside the range that is tracked by the sketch
            return
        }

        if (value > indexMapping.minIndexedValue) {
            positiveValueStore.add(indexMapping.getIndexFor(value))
        } else if (value < -indexMapping.minIndexedValue) {
            negativeValueStore.add(indexMapping.getIndexFor(-value))
        } else {
            zeroCount++
        }
    }

    fun isEmpty(): Boolean = zeroCount == 0.0 && negativeValueStore.isEmpty && positiveValueStore.isEmpty

    fun getCount(): Double = zeroCount + negativeValueStore.totalCount + positiveValueStore.totalCount

    fun getSum(): Double {
        var sum = 0.0
        negativeValueStore.forEach { index, count -> sum -= indexMapping.value(index) * count }
        positiveValueStore.forEach { index, count -> sum += indexMapping.value(index) * count }
        return sum
    }

    @VisibleForTesting
    internal fun serializedSize(): Int {
        return DDSketchSerializer.embeddedFieldSize(FIELD_MAPPING, indexMapping.serializedSize()) +
            DDSketchSerializer.embeddedFieldSize(FIELD_POSITIVE_VALUES, positiveValueStore.serializedSize()) +
            DDSketchSerializer.embeddedFieldSize(FIELD_NEGATIVE_VALUES, negativeValueStore.serializedSize()) +
            DDSketchSerializer.doubleFieldSize(FIELD_ZERO_COUNT, zeroCount)
    }

    @VisibleForTesting
    internal fun writeTo(serializer: DDSketchSerializer) {
        val indexMappingSize = indexMapping.serializedSize()
        val positiveValueStoreSize = positiveValueStore.serializedSize()
        val negativeValueStoreSize = negativeValueStore.serializedSize()
        serializer.writeHeader(FIELD_MAPPING, indexMappingSize)
        indexMapping.writeTo(serializer)
        serializer.writeHeader(FIELD_POSITIVE_VALUES, positiveValueStoreSize)
        positiveValueStore.writeTo(serializer)
        serializer.writeHeader(FIELD_NEGATIVE_VALUES, negativeValueStoreSize)
        negativeValueStore.writeTo(serializer)
        serializer.writeDouble(FIELD_ZERO_COUNT, zeroCount)
    }

    /**
     * Produces protobuf encoded bytes which are equivalent to using the official protobuf bindings,
     * without requiring a runtime dependency on protobuf-java.
     *
     * @return the sketch serialized as a [ByteArray].
     */
    fun serialize(): ByteArray {
        val serializer = DDSketchSerializer(serializedSize())
        writeTo(serializer)
        return serializer.toByteArray()
    }

    companion object {
        // Proto field numbers from DDSketch message in DDSketch.proto
        private const val FIELD_MAPPING = 1
        private const val FIELD_POSITIVE_VALUES = 2
        private const val FIELD_NEGATIVE_VALUES = 3
        private const val FIELD_ZERO_COUNT = 4
    }
}
