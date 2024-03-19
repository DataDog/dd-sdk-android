/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import androidx.compose.runtime.tooling.CompositionGroup
import com.datadog.android.sessionreplay.compose.internal.data.ComposeContext
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext

internal interface CompositionGroupMapper {
    fun map(
        compositionGroup: CompositionGroup,
        composeContext: ComposeContext,
        uiContext: UiContext
    ): ComposeWireframe?
}
