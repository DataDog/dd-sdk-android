/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.datadog.android.sample.R
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.compose.sessionReplayImagePrivacy
import com.datadog.android.sessionreplay.compose.sessionReplayTextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.sessionReplayTouchPrivacy

@Composable
internal fun FineGrainedMaskingSample() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ImagePrivacySample()
        TextAndInputPrivacySample()
        TouchPrivacySample()
    }
}

@Composable
private fun ImagePrivacySample() {
    Row(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        SampleItem(
            modifier = Modifier.weight(1f),
            title = "MASK_NONE"
        ) {
            Image(
                modifier = Modifier
                    .sessionReplayImagePrivacy(imagePrivacy = ImagePrivacy.MASK_NONE),
                painter = painterResource(R.drawable.ic_dd_icon_red),
                contentDescription = "red dog1"
            )
        }
        SampleItem(
            modifier = Modifier.weight(1f),
            title = "MASK_LARGE_ONLY"
        ) {
            Image(
                modifier = Modifier
                    .sessionReplayImagePrivacy(imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY),
                painter = painterResource(R.drawable.ic_dd_icon_red),
                contentDescription = "red dog2"
            )
        }
        SampleItem(
            modifier = Modifier.weight(1f),
            title = "MASK_ALL"
        ) {
            Image(
                modifier = Modifier
                    .sessionReplayImagePrivacy(imagePrivacy = ImagePrivacy.MASK_ALL),
                painter = painterResource(R.drawable.ic_dd_icon_red),
                contentDescription = "red dog3"
            )
        }
    }
}

@Composable
private fun TextAndInputPrivacySample() {
    Row {
        SampleItem(
            modifier = Modifier.wrapContentSize().weight(1f),
            title = "MASK_SENSITIVE_INPUTS"
        ) {
            Text(
                modifier = Modifier
                    .padding(16.dp)
                    .sessionReplayTextAndInputPrivacy(textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS),
                text = FAKE_TEXT
            )
        }
        SampleItem(
            modifier = Modifier.wrapContentSize().weight(1f),
            title = "MASK_ALL_INPUTS"
        ) {
            Text(
                modifier = Modifier
                    .padding(16.dp)
                    .sessionReplayTextAndInputPrivacy(textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL_INPUTS),
                text = FAKE_TEXT
            )
        }
        SampleItem(
            modifier = Modifier.wrapContentSize().weight(1f),
            title = "MASK_ALL"
        ) {
            Text(
                modifier = Modifier
                    .padding(16.dp)
                    .sessionReplayTextAndInputPrivacy(textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL),
                text = FAKE_TEXT
            )
        }
    }
}

@Composable
private fun TouchPrivacySample() {
    var hideClickTimes by remember {
        mutableStateOf(0)
    }
    var showClickTimes by remember {
        mutableStateOf(0)
    }
    Row {
        SampleItem(
            modifier = Modifier.wrapContentSize().weight(1f)
                .sessionReplayTouchPrivacy(touchPrivacy = TouchPrivacy.SHOW)
                .clickable {
                    showClickTimes++
                },
            title = "Touch Privacy: Show"
        ) {
            Text(
                text = if (showClickTimes == 0) {
                    "Click Me!"
                } else {
                    "You've clicked $showClickTimes times"
                }
            )
        }
        SampleItem(
            modifier = Modifier.wrapContentSize().weight(1f)
                .sessionReplayTouchPrivacy(touchPrivacy = TouchPrivacy.HIDE)
                .clickable {
                    hideClickTimes++
                },
            title = "Touch Privacy: Hide"
        ) {
            Text(
                modifier = Modifier
                    .sessionReplayTouchPrivacy(touchPrivacy = TouchPrivacy.HIDE),
                text = if (hideClickTimes == 0) {
                    "Click Me!"
                } else {
                    "You've clicked $hideClickTimes times"
                }
            )
        }
    }
}

@Composable
private fun SampleItem(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.border(
            width = 1.dp,
            color = Color.Black
        ).height(280.dp)
    ) {
        Text(
            modifier = Modifier.padding(16.dp).height(48.dp),
            text = title,
            textAlign = TextAlign.Center
        )
        Divider(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
        content.invoke(this)
    }
}

@Composable
@Preview(showBackground = true)
internal fun PreviewFineGrainedMaskingSample() {
    FineGrainedMaskingSample()
}

private const val FAKE_TEXT =
    "\"Beyond the silver mountains, a hidden village thrives under perpetual starlight. "
