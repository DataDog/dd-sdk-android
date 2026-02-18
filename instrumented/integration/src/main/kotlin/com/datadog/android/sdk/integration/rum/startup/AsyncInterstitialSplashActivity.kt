/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum.startup

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * Interstitial activity that launches the main activity and finishes itself asynchronously.
 * Unlike [InterstitialSplashActivity], the startActivity + finish calls are posted to a Handler,
 * causing the next activity's onCreate to fire AFTER this activity's onDestroy.
 *
 * This simulates real-world interstitial patterns where navigation is deferred
 * (e.g., auth checks via callbacks, deep link resolution, or framework-level async routing).
 */
internal class AsyncInterstitialSplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        finish()
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainContentActivity::class.java))
        }, NAVIGATION_DELAY_MS)
    }

    companion object {
        private const val NAVIGATION_DELAY_MS = 100L
    }
}
