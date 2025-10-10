/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.sessionreplay

import androidx.navigation.NavController
import com.datadog.sample.benchmark.R
import javax.inject.Inject

internal class SessionReplayNavigationManagerImpl @Inject constructor() :
    SessionReplayNavigationManager {
    private var navController: NavController? = null

    override fun setNavController(navController: NavController) {
        this.navController = navController
    }

    override fun navigateToSessionReplayMaterial() {
        navController?.navigate(R.id.fragment_session_replay_material)
    }

    override fun navigateToSessionReplayAppCompat() {
        navController?.navigate(R.id.fragment_session_replay_appcompat)
    }
}
