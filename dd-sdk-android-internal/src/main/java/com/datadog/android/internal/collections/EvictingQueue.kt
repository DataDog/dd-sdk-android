/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.collections

import java.util.LinkedList
import java.util.Queue
import kotlin.math.max

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
class EvictingQueue<T>(
    maxSize: Int = Int.MAX_VALUE,
    private val delegate: Queue<T> = LinkedList()
) : Queue<T> by delegate {

    override val size: Int
        get() = delegate.size
    private val maxSize: Int = max(0, maxSize)

    /**
     * Adds the specified [element] to the end of this queue.
     *
     * If the queue has reached its maximum capacity, the first (oldest) element is evicted (removed)
     * before the new element is added.
     *
     * This queue should never throw [IllegalStateException] due to capacity restriction of the [delegate] because it
     * uses [java.util.Queue.offer] to insert elements.
     *
     * @param element the element to be added.
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null and
     *         this queue does not permit null elements
     * @throws IllegalArgumentException if some property of this element
     *         prevents it from being added to this queue
     *
     * @return `true` if this collection changed as a result of the call (as specified by [java.util.Collection.add])
     *
     */
    override fun add(element: T): Boolean {
        if (maxSize == 0) return false
        if (size >= maxSize) {
            delegate.poll()
        }
        return delegate.offer(element)
    }

    /**
     * Adds the specified [element] to the end of this queue.
     *
     * If the queue has reached its maximum capacity, the first (oldest) element is evicted (removed)
     * before the new element is added.
     *
     * @param element the element to be added.
     *
     * @throws [ClassCastException] – if the class of the specified element prevents it from being added to this queue
     * @throws [NullPointerException] – if the specified element is null and this queue does not permit null elements
     * @throws [IllegalArgumentException] – if some property of this element prevents it from being added to this queue
     *
     * @return `true` if this collection changed as a result of the call
     */
    override fun offer(element: T): Boolean {
        if (maxSize == 0) return false
        if (size >= maxSize) {
            delegate.poll()
        }
        return delegate.offer(element)
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
    override fun offer(element: T): Boolean {
        if (maxSize == 0) return false
        if (size >= maxSize) {
            delegate.poll()
        }
        return delegate.offer(element)
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
        if (maxSize == 0) return false
        if (elements.size >= maxSize) {
            clear()
            for ((index, element) in elements.withIndex()) {
                if (index < elements.size - maxSize) continue
                delegate.add(element)
            }
            return true
        }

        val spaceLeft = maxSize - size
        for (index in 0 until elements.size - spaceLeft) {
            delegate.poll()
        }

        return delegate.addAll(elements)
    }
}
