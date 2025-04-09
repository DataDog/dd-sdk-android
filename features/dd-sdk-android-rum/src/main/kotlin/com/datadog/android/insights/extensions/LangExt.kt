/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights.extensions

import kotlin.math.max
import kotlin.math.min

internal fun Float.clip(size: Int, min: Int, max: Int): Float {
    return max(min(this, (max - size).toFloat()), min.toFloat())
}

internal fun <F, S> multiLet(first: F?, second: S?, block: (F, S) -> Unit) {
    if (first != null && second != null) block(first, second)
}
