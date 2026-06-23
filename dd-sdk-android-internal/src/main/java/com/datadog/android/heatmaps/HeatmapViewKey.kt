/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.heatmaps

import android.view.View

// Polynomial coefficient — matches Java's standard hashCode convention.
internal const val HEATMAP_VIEW_KEY_COEFFICIENT = 31L

/**
 * Returns the key used to store and look up a view's [HeatmapIdentifier] in a
 * [HeatmapIdentifierRegistry].
 *
 * The key combines the identity hash of the view with the identity hash of its direct parent
 * using a polynomial combination with coefficient 31 (the same coefficient used by Java's
 * standard `hashCode` convention). This makes the combinator non-commutative: swapping the
 * view and parent hashes produces a different key.
 *
 * Note: the returned value is an opaque 64-bit quantity and may be negative, since
 * [System.identityHashCode] returns a signed [Int] that is sign-extended on widening to [Long].
 * Callers must not assume the key is non-negative.
 */
fun heatmapViewKey(view: View): Long {
    val parentHash = view.parent?.let { System.identityHashCode(it) } ?: 0
    return HEATMAP_VIEW_KEY_COEFFICIENT * System.identityHashCode(view).toLong() + parentHash.toLong()
}
