/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

/**
 * Normalizes a Long value (font size, view dimension, view position, etc.) according with the
 * device pixels density.
 * Example: if a device has a DPI = 2, the normalized height of a view will be
 * view.height/2.
 * @param density
 */
internal fun Long.densityNormalized(density: Float): Long {
    if (density == 0f) {
        return this
    }
    return (this / density).toLong()
}
