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
    private var lastSnapshot: AccessibilityInfo? = null

    @Synchronized
    override fun getIfChanged(): AccessibilityInfo? {
        val newSnapshot = accessibilityReader.getState()
        if (newSnapshot == lastSnapshot) return null

        val deltaSnapshot = createDelta(newSnapshot, lastSnapshot)
        lastSnapshot = newSnapshot

        return if (deltaSnapshot.hasAnyNonNullValues()) deltaSnapshot else null
    }

    private fun createDelta(new: AccessibilityInfo, old: AccessibilityInfo?): AccessibilityInfo {
        return AccessibilityInfo(
            textSize =
            new.textSize.takeIf { it != old?.textSize },
            isScreenReaderEnabled =
            new.isScreenReaderEnabled.takeIf { it != old?.isScreenReaderEnabled },
            isColorInversionEnabled =
            new.isColorInversionEnabled.takeIf { it != old?.isColorInversionEnabled },
            isClosedCaptioningEnabled =
            new.isClosedCaptioningEnabled.takeIf { it != old?.isClosedCaptioningEnabled },
            isReducedAnimationsEnabled =
            new.isReducedAnimationsEnabled.takeIf { it != old?.isReducedAnimationsEnabled },
            isScreenPinningEnabled =
            new.isScreenPinningEnabled.takeIf { it != old?.isScreenPinningEnabled },
            isRtlEnabled =
            new.isRtlEnabled.takeIf { it != old?.isRtlEnabled }
        )
    }

    private fun AccessibilityInfo.hasAnyNonNullValues(): Boolean {
        return setOf<Any?>(
            this.textSize,
            this.isScreenReaderEnabled,
            this.isRtlEnabled,
            this.isScreenPinningEnabled,
            this.isColorInversionEnabled,
            this.isClosedCaptioningEnabled,
            this.isReducedAnimationsEnabled
        ).any { it != null }
    }
}
