/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.activities.scenarios

import android.os.Bundle
import androidx.activity.compose.setContent
import com.datadog.benchmark.sample.ui.sessionreplaycompose.MainView

class SessionReplayComposeScenarioActivity : BaseScenarioActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        benchmarkActivityComponent.inject(this)

        supportActionBar?.hide()
        setContent {
            MainView()
        }
    }
}
