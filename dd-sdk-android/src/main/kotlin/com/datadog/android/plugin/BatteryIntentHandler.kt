/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.plugin

import android.content.Intent

/**
 * Results based on values found in the battery intent.
 */
data class BatteryIntentResults(
    /**
     * Indicator of battery charge state.
     */
    val batteryFullOrCharging:Boolean,
    /**
     * Battery charge level.
     */
    val batteryLevel: Int,
    /**
     * Indicator of external power applied.
     */
    val onExternalPowerSource: Boolean
    )

/**
 * The purpose behind the BatteryIntentHandler is to have the ability to modify how the system Battery intent is processed.
 * Modifications of this intent processing could have negative effects on the users battery life and it is highly recommended to
 * not provide your own BatteryIntentHandler unless there are very specific needs to override the default functionality.
 */
interface BatteryIntentHandler {
    /**
     * Process the system battery intent returning results based on intent values.
     * @param intent - System battery intent
     */
    fun handleBatteryIntent(intent: Intent): BatteryIntentResults
}