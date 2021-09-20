/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.nightly.R
import com.datadog.android.nightly.utils.measure

internal class ViewTrackingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tracking_strategy_activity)
    }

    override fun onResume() {
        measure(TEST_METHOD_NAME) {
            super.onResume()
        }
    }

    companion object {
        private const val TEST_METHOD_NAME = "rum_activity_view_tracking_strategy"
    }
}
