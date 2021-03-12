/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.log.Logger
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig

internal class ActivityProfiling : AppCompatActivity() {

    lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger = RuntimeConfig.logger()
        setContentView(R.layout.main_activity_layout)
    }
}
