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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil3.request.ImageRequest
import com.datadog.android.Datadog
import com.datadog.android.coil3.DatadogCoilRequestListener
import com.datadog.android.sample.R
import coil3.compose.AsyncImage as AsyncImage3

private const val SMALL_IMAGE_URL = "https://picsum.photos/100/100"

@Composable
internal fun ImageSample() {
    Column {
        Text(
            "Content Scale Section",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(8.dp)
        )
        ImageScaling()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Image Loading Section",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(8.dp)
        )
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
            item {
                CoilImage()
            }
            item {
                Coil3Image()
            }
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
private fun CoilImage() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            modifier = Modifier.imageModifier(),
            model = SMALL_IMAGE_URL,
            contentScale = ContentScale.Fit,
            contentDescription = "Network Image (Coil 2)"
        )
        DescriptionText("Network Image (Coil 2)")
    }
}

@Composable
private fun Coil3Image() {
    val context = LocalContext.current
    val listener = remember { DatadogCoilRequestListener(Datadog.getInstance()) }
    val imageRequest = remember(context) {
        ImageRequest.Builder(context)
            .data(SMALL_IMAGE_URL)
            .listener(listener)
            .build()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage3(
            modifier = Modifier.imageModifier(),
            model = imageRequest,
            contentScale = ContentScale.Fit,
            contentDescription = "Network Image (Coil 3)"
        )
        DescriptionText("Network Image (Coil 3)")
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

@Composable
@Preview(showBackground = true)
@Suppress("UnusedPrivateMember")
private fun PreviewImageSample() {
    ImageSample()
}
