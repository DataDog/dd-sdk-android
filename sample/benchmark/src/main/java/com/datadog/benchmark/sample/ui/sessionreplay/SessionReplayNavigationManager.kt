/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.sessionreplay

import androidx.navigation.NavController

internal interface SessionReplayNavigationManager {
    fun setNavController(navController: NavController)
    fun navigateToSessionReplayMaterial()
    fun navigateToSessionReplayAppCompat()
}
