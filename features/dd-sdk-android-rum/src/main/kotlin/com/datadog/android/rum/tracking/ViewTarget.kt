/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.view.View
import com.datadog.android.lint.InternalApi
import java.lang.ref.WeakReference

/**
 * Represents the result of locating a target view in response to a user interaction,
 * such as a tap or scroll event in [GesturesListener].
 *
 * @property viewRef The Weak Reference of Android [View] that was found.
 *                     If non-null, indicates a classic View was located.
 * @property node The semantics node associated with a Jetpack Compose component. If non-null, indicates
 *               that a Compose node with the given semantics tag was found.
 *
 * Only one of [viewRef] or [node] is expected to be non-null, depending on the UI framework used.
 */
class ViewTarget(
    val viewRef: WeakReference<View?> = WeakReference(null),
    val node: Node? = null
) {

    override fun equals(other: Any?): Boolean {
        // Overriding hashcode & equals because we should compare the referent
        // instead of the reference.
        if (this === other) return true
        if (other !is ViewTarget) return false

        if (viewRef.get() != other.viewRef.get()) return false
        if (node != other.node) return false

        return true
    }

    override fun hashCode(): Int {
        var result = viewRef.get().hashCode()
        result = 31 * result + node.hashCode()
        return result
    }
}

/**
 * Represents the result of locating a target node in Jetpack compose.
 * @property name the name of the target node.
 * @property customAttributes the custom attributes that the target node may have.
 */
@InternalApi
data class Node(
    val name: String,
    val customAttributes: Map<String, Any?> = mapOf()
)
