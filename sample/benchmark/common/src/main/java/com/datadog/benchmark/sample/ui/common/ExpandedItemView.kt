/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun <T> ExpandedItemView(
    titleText: String,
    items: List<T>,
    headerText: String,
    itemTextFactory: (T) -> String,
    onClick: (T) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = titleText, modifier = Modifier.weight(1f))

        DropDownMenuView(
            headerText = headerText,
            items = items,
            textFactory = { Text(text = itemTextFactory(it)) },
            onClickAction = { onClick(it) }
        )
    }
}
