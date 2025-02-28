/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.core.collections

import java.util.LinkedList

class EvictingQueue<T>(private val maxSize: Int) : LinkedList<T>() {

    init {
        if (maxSize <= 0) throw IllegalArgumentException("maxSize should be > 0")
    }

    override fun add(element: T): Boolean {
        if (size >= maxSize) {
            removeFirst()
        }
        return super.add(element)
    }

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

    override fun add(index: Int, element: T) {
        throw UnsupportedOperationException("Insertion by index is not supported in EvictingQueue")
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        if (index == size) return super.addAll(index, elements)
        throw UnsupportedOperationException("Insertion by index is not supported in EvictingQueue")
    }
}
