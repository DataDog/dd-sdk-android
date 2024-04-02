/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.utils

import android.os.Bundle

fun Bundle?.asMap(): Map<String, Any?> {
    if (this == null) {
        return emptyMap()
    }

    return keySet()
        .fold(mutableMapOf()) { map, key ->
            // TODO RUM-503 Bundle#get is deprecated, but there is no replacement for it.
            // Issue is opened in the Google Issue Tracker.
            @Suppress("DEPRECATION")
            map[key] = this[key]
            map
        }
}
