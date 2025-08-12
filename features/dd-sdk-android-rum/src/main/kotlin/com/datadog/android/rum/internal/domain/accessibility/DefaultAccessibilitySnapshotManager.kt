/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

import com.datadog.android.rum.internal.domain.InfoProvider

internal class DefaultAccessibilitySnapshotManager(
    private val accessibilityReader: InfoProvider
) : AccessibilitySnapshotManager {
    private val lastSnapshot = mutableMapOf<String, Any>()

    @Synchronized
    override fun latestSnapshot(): Accessibility {
        val newAccessibilityState = accessibilityReader.getState()
        val newSnapshot = mutableMapOf<String, Any>()

        // remove the ones we saw already
        for (key in newAccessibilityState.keys) {
            val newValue = newAccessibilityState[key]
                ?: continue

            val oldValue = lastSnapshot[key]

            if (newValue != oldValue) {
                newSnapshot[key] = newValue
                lastSnapshot[key] = newValue
            }
        }

        return Accessibility.fromMap(newSnapshot)
    }
}
