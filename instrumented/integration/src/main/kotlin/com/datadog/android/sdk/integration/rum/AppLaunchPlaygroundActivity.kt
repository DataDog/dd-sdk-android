/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.integration.R

@OptIn(ExperimentalRumApi::class)
internal class AppLaunchPlaygroundActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity_layout)
    }

    override fun onResume() {
        super.onResume()

        @Suppress("MagicNumber")
        window.decorView.postDelayed(
            { GlobalRumMonitor.get().reportAppFullyDisplayed() },
            3000
        )
    }
}
