/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

import com.datadog.android.rum.internal.domain.InfoProvider

internal class DefaultAccessibilitySnapshotManager(
    private val accessibilityReader: InfoProvider<AccessibilityInfo>
) : AccessibilitySnapshotManager {
    @Volatile
    private var lastSnapshot = AccessibilityInfo()

    @Synchronized
    override fun latestSnapshot(): AccessibilityInfo {
        val newAccessibilitySnapshot = accessibilityReader.getState()

        // Create delta by comparing new snapshot to last snapshot
        // Only include properties that have changed (null for unchanged properties)
        val deltaSnapshot = AccessibilityInfo(
            textSize =
            if (newAccessibilitySnapshot.textSize != lastSnapshot.textSize) {
                newAccessibilitySnapshot.textSize
            } else {
                null
            },

            isScreenReaderEnabled =
            if (newAccessibilitySnapshot.isScreenReaderEnabled != lastSnapshot.isScreenReaderEnabled) {
                newAccessibilitySnapshot.isScreenReaderEnabled
            } else {
                null
            },

            isColorInversionEnabled =
            if (newAccessibilitySnapshot.isColorInversionEnabled != lastSnapshot.isColorInversionEnabled) {
                newAccessibilitySnapshot.isColorInversionEnabled
            } else {
                null
            },

            isClosedCaptioningEnabled =
            if (newAccessibilitySnapshot.isClosedCaptioningEnabled != lastSnapshot.isClosedCaptioningEnabled) {
                newAccessibilitySnapshot.isClosedCaptioningEnabled
            } else {
                null
            },

            isReducedAnimationsEnabled =
            if (newAccessibilitySnapshot.isReducedAnimationsEnabled != lastSnapshot.isReducedAnimationsEnabled) {
                newAccessibilitySnapshot.isReducedAnimationsEnabled
            } else {
                null
            },

            isScreenPinningEnabled =
            if (newAccessibilitySnapshot.isScreenPinningEnabled != lastSnapshot.isScreenPinningEnabled) {
                newAccessibilitySnapshot.isScreenPinningEnabled
            } else {
                null
            },

            isRtlEnabled =
            if (newAccessibilitySnapshot.isRtlEnabled != lastSnapshot.isRtlEnabled) {
                newAccessibilitySnapshot.isRtlEnabled
            } else {
                null
            }
        )

        // Update last snapshot for future comparisons
        lastSnapshot = newAccessibilitySnapshot

        return deltaSnapshot
    }
}
