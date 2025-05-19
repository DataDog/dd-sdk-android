/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datadog.android.sample.R

@Composable
@Suppress("MagicNumber")
internal fun ImageScaling() {
    LazyVerticalGrid(
        modifier = Modifier.wrapContentSize(),
        columns = GridCells.Fixed(4),
        userScrollEnabled = false
    ) {
        imageScalingItem(ContentScale.Fit, "Fit")
        imageScalingItem(ContentScale.Crop, "Crop")
        imageScalingItem(ContentScale.Inside, "Inside")
        imageScalingItem(ContentScale.FillWidth, "FillWidth")
        imageScalingItem(ContentScale.FillHeight, "FillHeight")
        imageScalingItem(ContentScale.FillBounds, " FillBounds")
        imageScalingItem(ContentScale.None, "None")
    }
}

private fun LazyGridScope.imageScalingItem(
    contentScale: ContentScale,
    text: String
) {
    item {
        Column(
            modifier = Modifier.padding(8.dp).wrapContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                modifier = Modifier.background(color = Color.Green).size(72.dp),
                painter = painterResource(R.drawable.example_appwidget_preview),
                contentDescription = "image",
                contentScale = contentScale
            )
            Text(text = text, fontSize = 12.sp)
        }
    }
}

@Composable
@Preview(showBackground = true)
internal fun PreviewImageScaling() {
    ImageScaling()
}
