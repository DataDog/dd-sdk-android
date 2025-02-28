/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.core.collections

import java.util.LinkedList

/**
 * A bounded queue that automatically evicts the oldest elements when new elements are added beyond its maximum capacity.
 *
 * This class extends [LinkedList] to provide a FIFO (first-in, first-out) queue with a fixed maximum size.
 * When adding new elements, if the queue is already at capacity, the oldest element is automatically removed.
 *
 * @param T the type of elements held in this collection.
 * @param maxSize the maximum number of elements the queue can hold. Must be greater than 0.
 *                The default value is [Int.MAX_VALUE], which effectively means there is no practical bound.
 *
 * @throws IllegalArgumentException if [maxSize] is less than or equal to 0.
 */
class EvictingQueue<T>(private val maxSize: Int = Int.MAX_VALUE) : LinkedList<T>() {

    init {
        @Suppress("UnsafeThirdPartyFunctionCall") // this line should throw an exception
        require(maxSize > 0) { "maxSize should be > 0" }
    }

    /**
     * Adds the specified [element] to the end of this queue.
     *
     * If the queue has reached its maximum capacity, the first (oldest) element is evicted (removed)
     * before the new element is added.
     *
     * @param element the element to be added.
     * @return `true` if this collection changed as a result of the call
     */
    override fun add(element: T): Boolean {
        if (size >= maxSize) {
            removeFirst()
        }
        return super.add(element)
    }

    /**
     * Adds all of the elements in the specified [elements] collection to the end of this queue.
     *
     * If the number of elements in [elements] is greater than or equal to [maxSize], the queue is cleared first,
     * and only the last [maxSize] elements from [elements] are added.
     *
     * Otherwise, if adding [elements] would exceed the maximum capacity, the required number of oldest elements
     * are evicted from the front of the queue to make room.
     *
     * @param elements the collection of elements to be added.
     * @return `true` if the queue changed as a result of the call.
     */
    override fun addAll(elements: Collection<T>): Boolean {
        if (elements.size >= maxSize) {
            clear()
            for ((index, element) in elements.withIndex()) {
                if (index < elements.size - maxSize) continue
                super.add(element)
            }
            return true
        }

        val spaceLeft = maxSize - size
        for (index in 0 until elements.size - spaceLeft) {
            removeFirst()
        }

        return super.addAll(elements)
    }

    /**
     * This operation is not supported.
     *
     * Insertion at an arbitrary [index] is not allowed in [EvictingQueue] because it would break the
     * FIFO (first-in, first-out) eviction policy.
     *
     * @param index the index at which the element is to be inserted.
     * @param element the element to be inserted.
     * @throws UnsupportedOperationException always.
     */
    override fun add(index: Int, element: T) {
        throw UnsupportedOperationException("Insertion by index is not supported in EvictingQueue")
    }

    /**
     * This operation is not supported.
     *
     * Insertion of a collection at an arbitrary [index] is not allowed in [EvictingQueue].
     * The only allowed insertion is at the end of the queue (i.e., when [index] equals the current size).
     *
     * @param index the index at which to insert the first element from [elements].
     * @param elements the collection of elements to be added.
     * @return nothing; this method always throws an exception if [index] is not at the end.
     * @throws UnsupportedOperationException if [index] is not equal to the current size of the queue.
     */
    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        if (index == size) return super.addAll(index, elements)
        throw UnsupportedOperationException("Insertion by index is not supported in EvictingQueue")
    }
}
