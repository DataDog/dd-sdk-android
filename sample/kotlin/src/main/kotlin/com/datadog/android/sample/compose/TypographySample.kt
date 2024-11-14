/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun TypographySample() {
    Column {
        Text(
            text = "Material H3 - onBackground",
            style = MaterialTheme.typography.h3,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colors.onBackground
        )

        Text(
            text = "Material H4 - onSurface",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colors.onSurface
        )

        Text(
            text = "Material H6 - Red",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(16.dp),
            color = Color.Red
        )

        Text(
            text = "Material Body 1 - Green",
            style = MaterialTheme.typography.body1.copy(
                color = Color.Blue
            ),
            modifier = Modifier.padding(16.dp),
            color = Color.Green
        )

        Text(
            text = "Material Caption - Blue",
            style = MaterialTheme.typography.caption.copy(
                color = Color.Blue
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewTypographySample() {
    TypographySample()
}
