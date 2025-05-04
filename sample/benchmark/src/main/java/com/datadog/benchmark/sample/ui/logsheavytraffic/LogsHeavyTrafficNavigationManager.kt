/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logsheavytraffic

import androidx.navigation.NavController
import javax.inject.Inject

internal class LogsHeavyTrafficNavigationManager @Inject constructor(
    val navController: NavController,
) {
    fun openSettings() {
        navController.navigate("logs_heavy_traffic_settings")
    }

    fun closeSettings() {
        navController.popBackStack()
    }
}
