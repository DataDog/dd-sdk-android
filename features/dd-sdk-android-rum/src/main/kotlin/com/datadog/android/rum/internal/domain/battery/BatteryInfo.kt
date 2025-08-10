/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.battery

/**
 * Provides information about the battery state.
 *
 * @property batteryLevel the current battery charge level, expressed as a float from 0.0f (empty) to 1.0f (full).
 * @property lowPowerMode a boolean indicating whether the device is currently in Low Power Mode.
 */
internal data class BatteryInfo(
    val batteryLevel: Float? = null,
    val lowPowerMode: Boolean? = null
)
