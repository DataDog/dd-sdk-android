/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.nightly.R

internal class UserInteractionTrackingActivity : AppCompatActivity() {

    val testMethodName = "rum_user_interaction_tracking_strategy"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_interaction_tracking_strategy_activity)
        findViewById<Button>(R.id.user_interaction_strategy_button).setOnClickListener {
            it.visibility = View.GONE
        }
    }
}
