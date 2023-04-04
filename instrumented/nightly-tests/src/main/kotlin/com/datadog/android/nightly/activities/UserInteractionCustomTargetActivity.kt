/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.nightly.R
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumResourceKind
import java.net.HttpURLConnection

internal class UserInteractionCustomTargetActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_interaction_tracking_strategy_activity)
        val key = UserInteractionCustomTargetActivity::class.java.simpleName

        findViewById<Button>(R.id.user_interaction_strategy_button).setOnClickListener {
            val sdkCore = Datadog.getInstance()
            if (sdkCore != null) {
                GlobalRum.get(sdkCore).startResource(
                    key,
                    "get",
                    key
                )
                GlobalRum.get(sdkCore).stopResource(
                    key,
                    HttpURLConnection.HTTP_OK,
                    FAKE_RESOURCE_DOWNLOADED_BYTES,
                    RumResourceKind.IMAGE,
                    emptyMap()
                )
            }
        }
    }
}
