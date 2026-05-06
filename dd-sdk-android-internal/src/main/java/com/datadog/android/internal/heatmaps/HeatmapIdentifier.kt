/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.heatmaps

/**
 * A stable, globally unique identifier for a UI element, used to correlate
 * RUM tap actions with Session Replay wireframes for heatmap rendering.
 *
 * @property rawValue the hex-encoded hash string that uniquely identifies the element.
 */
data class HeatmapIdentifier(val rawValue: String) {

    companion object {

        private const val SEPARATOR = "/"

        internal fun canonicalPath(
            elementPath: List<String>,
            screenName: String,
            bundleIdentifier: String
        ): String = buildString {
            append(bundleIdentifier)
            append(SEPARATOR)
            append(screenName)
            for (segment in elementPath) {
                append(SEPARATOR)
                append(segment)
            }
        }
    }
}
