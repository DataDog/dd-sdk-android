/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.compose

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayActivity

internal class ComposeImageActivity : BaseSessionReplayActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImageSample()
        }
    }

    @Composable
    internal fun ImageSample() {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                LocalImageNoBackground()
            }

            item {
                LocalIconDoubleBackground()
            }

            item {
                IconButtonSingleBackground()
            }
        }
    }

    @Composable
    private fun LocalImageNoBackground() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                modifier = Modifier.imageModifier(),
                painter = painterResource(R.drawable.ic_dd_icon_rgb),
                contentDescription = "purple dog"
            )
            DescriptionText("Image")
        }
    }

    @Composable
    private fun LocalIconDoubleBackground() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.Green)
                    .padding(32.dp)
                    .size(128.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
                    .padding(4.dp),
                painter = painterResource(R.drawable.ic_dd_icon_red),
                tint = Color.Red,
                contentDescription = "red dog"
            )
            DescriptionText("Icon")
        }
    }

    @Composable
    private fun IconButtonSingleBackground() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                modifier = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                onClick = {}
            ) {
                Icon(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(160.dp),
                    painter = painterResource(R.drawable.ic_dd_icon_white),
                    tint = Color.White,
                    contentDescription = "white dog"
                )
            }
            DescriptionText("Icon Button")
        }
    }

    @Composable
    private fun DescriptionText(description: String) {
        Text(
            description,
            fontSize = 20.sp
        )
    }

    private fun Modifier.imageModifier(): Modifier {
        return this
            .padding(32.dp)
            .size(160.dp)
    }
}
