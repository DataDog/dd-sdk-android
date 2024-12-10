/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Suppress("LongMethod")
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
        Text(
            text = FAKE_LONG_TEXT,
            style = MaterialTheme.typography.caption.copy(
                color = Color.Black
            ),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .height(60.dp)
                .fillMaxWidth()
                .background(color = Color.Yellow)
                .padding(16.dp)
        )
        Row {
            Text(
                text = FAKE_LONG_TEXT,
                style = MaterialTheme.typography.caption.copy(
                    color = Color.Black
                ),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Red)
                    .padding(16.dp),
                maxLines = 1
            )
            Text(
                text = FAKE_LONG_TEXT,
                style = MaterialTheme.typography.caption.copy(
                    color = Color.Black
                ),
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Green)
                    .padding(16.dp),
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewTypographySample() {
    TypographySample()
}

private const val FAKE_LONG_TEXT =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quisque posuere arcu eget est " +
        "interdum, ac eleifend ex laoreet. Integer fringilla eros sed velit dapibus, sit" +
        " amet egestas urna faucibus. Suspendisse potenti. Nulla facilisi. Proin sagittis " +
        "eros eu nulla fringilla, quis consequat sapien tempus. Sed vel feugiat leo. Etiam " +
        "id ultricies odio. Donec luctus sem vel magna consequat auctor. Fusce bibendum mi" +
        " sed sapien faucibus, id scelerisque ligula hendrerit."
