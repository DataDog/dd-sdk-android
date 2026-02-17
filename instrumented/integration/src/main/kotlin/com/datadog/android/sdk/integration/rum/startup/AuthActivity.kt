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
 * Interstitial activity that simulates an authentication check.
 * It immediately launches [InterstitialSplashActivity] and finishes itself
 * without ever drawing a frame.
 *
 * Used in chain tests: AuthActivity → SplashActivity → MainContentActivity,
 * where both auth and splash are non-drawing interstitials.
 */
internal class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immediately launch splash activity without setting content view
        val intent = Intent(this, InterstitialSplashActivity::class.java)
        startActivity(intent)
        finish()
    }
}
