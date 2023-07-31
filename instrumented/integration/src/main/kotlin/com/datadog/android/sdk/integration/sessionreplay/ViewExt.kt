/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.view.View
import android.view.ViewGroup
import java.util.LinkedList

internal fun View.resolveId(): Long {
    return System.identityHashCode(this).toLong()
}

internal fun View.getViewAbsoluteCoordinates(): IntArray {
    val coordinates = IntArray(2)
    getLocationOnScreen(coordinates)
    return coordinates
}

internal inline fun <reified T> View.findViewByType(type: Class<T>): T? {
    val groups = LinkedList<ViewGroup>()
    (this as? ViewGroup)?.let { groups.add(it) }
    while (groups.isNotEmpty()) {
        val group = groups.remove()
        for (i in 0 until group.childCount) {
            val childAt = group.getChildAt(i)
            if (type.isAssignableFrom(childAt.javaClass)) {
                return childAt as T
            }
            if (childAt is ViewGroup) {
                groups.add(childAt)
            }
        }
    }
    return null
}
