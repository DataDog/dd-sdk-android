/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.compose

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayActivity

internal class ComposeButtonActivity : BaseSessionReplayActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ButtonSample()
        }
    }

    @Composable
    private fun ButtonSample() {
        Column {
            Button(onClick = {
            }) {
                Text("Text Button")
            }

            Button(onClick = {
            }) {
                Image(
                    painter = painterResource(R.drawable.ic_dd_icon_red),
                    contentDescription = "image button"
                )
            }
        }
    }
}
