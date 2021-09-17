/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.nightly.R
import com.datadog.android.nightly.fragments.ViewTrackingMixedHomeFragment

internal class ViewTrackingMixedFragmentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tracking_strategy_fragment_activity)
        supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, ViewTrackingMixedHomeFragment::class.java, null)
            .commit()
    }
}
