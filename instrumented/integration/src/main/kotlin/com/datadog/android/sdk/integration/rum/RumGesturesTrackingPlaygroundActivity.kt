/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.datadog.android.sdk.integration.R

internal class RumGesturesTrackingPlaygroundActivity : Activity() {

    lateinit var button: Button
    lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gestures_tracking_layout)

        button = findViewById(R.id.button)
        textView = findViewById(R.id.textView)
    }
}
