/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

import com.datadog.android.rum.internal.domain.InfoData

/**
 * Represents the accessibility settings state of the device.
 *
 * @property textSize The font scale factor (1.0 = normal, >1.0 = larger, <1.0 = smaller)
 * @property isScreenReaderEnabled Whether touch exploration is enabled (TalkBack, etc.)
 * @property isColorInversionEnabled Whether color inversion is enabled
 * @property isClosedCaptioningEnabled Whether closed captions are enabled
 * @property isReducedAnimationsEnabled Whether animations are disabled/reduced
 * @property isScreenPinningEnabled Whether the device is in single-app mode
 * @property isRtlEnabled Whether right to left layout is enabled
 */
internal data class AccessibilityInfo(
    val textSize: String? = null,
    val isScreenReaderEnabled: Boolean? = null,
    val isColorInversionEnabled: Boolean? = null,
    val isClosedCaptioningEnabled: Boolean? = null,
    val isReducedAnimationsEnabled: Boolean? = null,
    val isScreenPinningEnabled: Boolean? = null,
    val isRtlEnabled: Boolean? = null
) : InfoData {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccessibilityInfo

        if (isScreenReaderEnabled != other.isScreenReaderEnabled) return false
        if (isColorInversionEnabled != other.isColorInversionEnabled) return false
        if (isClosedCaptioningEnabled != other.isClosedCaptioningEnabled) return false
        if (isReducedAnimationsEnabled != other.isReducedAnimationsEnabled) return false
        if (isScreenPinningEnabled != other.isScreenPinningEnabled) return false
        if (isRtlEnabled != other.isRtlEnabled) return false
        if (textSize != other.textSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isScreenReaderEnabled?.hashCode() ?: 0
        result = 31 * result + (isColorInversionEnabled?.hashCode() ?: 0)
        result = 31 * result + (isClosedCaptioningEnabled?.hashCode() ?: 0)
        result = 31 * result + (isReducedAnimationsEnabled?.hashCode() ?: 0)
        result = 31 * result + (isScreenPinningEnabled?.hashCode() ?: 0)
        result = 31 * result + (isRtlEnabled?.hashCode() ?: 0)
        result = 31 * result + (textSize?.hashCode() ?: 0)
        return result
    }
}
