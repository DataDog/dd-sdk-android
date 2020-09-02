/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import androidx.window.DeviceState
import androidx.window.DisplayFeature

internal data class DeviceInfo(
    val state: Posture = Posture.UNKNOWN,
    val displayType: DisplayType? = null
) {

    internal enum class Posture(val serializedName:String) {
        UNKNOWN("unknown"),
        CLOSED("closed"),
        HALF_OPENED("half_opened"),
        OPENED("opened"),
        FLIPPED("flipped");

        companion object {
            fun fromPosture(posture: Int): Posture {
                return when (posture) {
                    DeviceState.POSTURE_CLOSED -> CLOSED
                    DeviceState.POSTURE_FLIPPED -> FLIPPED
                    DeviceState.POSTURE_HALF_OPENED -> HALF_OPENED
                    DeviceState.POSTURE_OPENED -> OPENED
                    else -> UNKNOWN
                }
            }
        }
    }

    internal enum class DisplayType(val serializedName: String) {
        UNKNOWN("unknown"),
        HINGED("hinged"),
        FOLD("fold");

        companion object {
            fun fromType(type: Int): DisplayType {
                return when (type) {
                    DisplayFeature.TYPE_FOLD -> FOLD
                    DisplayFeature.TYPE_HINGE -> HINGED
                    else -> UNKNOWN
                }
            }
        }
    }
}