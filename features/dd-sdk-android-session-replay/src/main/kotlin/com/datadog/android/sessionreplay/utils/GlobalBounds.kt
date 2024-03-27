/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

/**
 * Defines the dimension and positions in Global coordinates for a geometry. By Global we mean that
 * the View position will not be relative to its parent but to the Device screen.
 * These dimensions are already normalized according with the current device screen density.
 * Example: if a device has a DPI = 2, the value of the dimension or position is divided by 2 to get
 * a normalized value.
 * @param x as the position on X axis
 * @param y as the position on Y axis
 * @param width as the width
 * @param height as the height
 */
data class GlobalBounds(
    val x: Long,
    val y: Long,
    val width: Long,
    val height: Long
)
