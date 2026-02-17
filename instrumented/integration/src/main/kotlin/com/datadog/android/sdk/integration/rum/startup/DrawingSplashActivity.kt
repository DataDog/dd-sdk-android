/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum.startup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.sdk.integration.R

/**
 * Splash activity that draws a layout before navigating to the main activity.
 * Unlike [InterstitialSplashActivity], this activity sets a content view and renders
 * a frame before starting the next activity. This simulates splash screens that
 * display a logo or loading indicator before navigating away.
 *
 * The predicate is needed to exclude this activity from TTID measurement because
 * auto-forwarding only handles activities that are destroyed without drawing a frame.
 */
internal class DrawingSplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.drawing_splash_layout)

        // Navigate after the first frame is drawn, ensuring the activity actually renders
        window.decorView.post {
            val intent = Intent(this, MainContentActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
