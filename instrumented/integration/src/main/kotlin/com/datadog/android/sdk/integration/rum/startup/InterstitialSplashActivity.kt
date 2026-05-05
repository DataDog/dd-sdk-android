/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum.startup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Interstitial activity that immediately launches the main activity and finishes itself.
 * This simulates splash screens or authentication activities that never draw a frame.
 */
internal class InterstitialSplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immediately launch main activity without setting content view
        val intent = Intent(this, MainContentActivity::class.java)
        startActivity(intent)
        finish()
    }
}
