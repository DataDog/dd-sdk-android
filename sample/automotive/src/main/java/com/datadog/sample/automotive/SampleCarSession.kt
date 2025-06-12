/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.sample.automotive

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.datadog.sample.automotive.screen.HomeScreen

class SampleCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        SharedLogger.logger.i("onCreateScreen $intent")
        return HomeScreen(carContext)
    }

    override fun onNewIntent(intent: Intent) {
        SharedLogger.logger.i("onNewIntent $intent")
        super.onNewIntent(intent)
    }
}
