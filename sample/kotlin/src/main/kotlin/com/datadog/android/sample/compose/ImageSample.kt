/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.datadog.android.sample.R
private const val SMALL_IMAGE_URL = "https://picsum.photos/100/100"

@Composable
internal fun ImageSample() {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                modifier = Modifier.imageModifier(),
                painter = painterResource(R.drawable.ic_dd_icon_rgb),
                contentDescription = "purple dog"
            )
            DescriptionText("Image")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier.imageModifier().background(Color.DarkGray),
                painter = painterResource(R.drawable.ic_dd_icon_red),
                tint = Color.Red,
                contentDescription = "red dog"
            )
            DescriptionText("Icon")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                modifier = Modifier
                    .padding(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black),
                onClick = {}
            ) {
                Icon(
                    modifier = Modifier
                        .padding(16.dp)
                        .width(64.dp).height(64.dp),
                    painter = painterResource(R.drawable.ic_dd_icon_white),
                    tint = Color.White,
                    contentDescription = "white dog"
                )
            }
            DescriptionText("Icon Button")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                modifier = Modifier.imageModifier(),
                model = SMALL_IMAGE_URL,
                contentDescription = "Network Image"
            )
            DescriptionText("Network Image")
        }
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
        .width(64.dp).height(64.dp)
}

@Composable
@Preview(showBackground = true)
@Suppress("UnusedPrivateMember")
private fun PreviewImageSample() {
    ImageSample()
}
