/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.compose

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Checkbox
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.runtime.Composable
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayActivity

internal class ComposeSelectableActivity : BaseSessionReplayActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SelectableSample()
        }
    }

    @Composable
    private fun SelectableSample() {
        Column {
            Checkbox(checked = true, onCheckedChange = {})
            Checkbox(checked = false, onCheckedChange = {})

            Switch(checked = true, onCheckedChange = {})
            Switch(checked = false, onCheckedChange = {})

            RadioButton(selected = true, onClick = {})
            RadioButton(selected = false, onClick = {})
        }
    }
}
