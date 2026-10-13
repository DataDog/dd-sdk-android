/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.activities

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

internal abstract class BaseBenchmarkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Capture the window container before AppCompat installs its own content container,
        // so the padding protects the action bar as well as every View and Compose screen.
        val content = window.decorView.findViewById<View>(android.R.id.content)
        val initialPadding = Insets.of(
            content.paddingLeft,
            content.paddingTop,
            content.paddingRight,
            content.paddingBottom
        )
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                initialPadding.left + safeInsets.left,
                initialPadding.top + safeInsets.top,
                initialPadding.right + safeInsets.right,
                initialPadding.bottom + safeInsets.bottom
            )
            // Descendants receive only the remaining insets, preventing duplicate bar padding
            // while preserving keyboard insets.
            insets.inset(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom)
        }

        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Edge-to-edge exposes the light window background behind the system bars.
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
        ViewCompat.requestApplyInsets(content)
    }
}
