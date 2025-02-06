/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.os.Bundle

internal const val ARGUMENT_TAG = "view.arguments"

/**
 * Converts this bundle into a Map of attributes to be included in a RUM View event.
 */
fun Bundle?.convertToRumViewAttributes(): MutableMap<String, Any?> {
    if (this == null) return mutableMapOf()

    val attributes = mutableMapOf<String, Any?>()

    keySet().forEach {
        // TODO RUM-503 Bundle#get is deprecated, but there is no replacement for it.
        // Issue is opened in the Google Issue Tracker.
        @Suppress("DEPRECATION")
        attributes["$ARGUMENT_TAG.$it"] = get(it)
    }

    return attributes
}
