/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.tools.diff.Diff
import com.datadog.tools.diff.DiffAppend
import com.datadog.tools.diff.DiffMerge

@Diff
internal data class RumViewState(
    val x: Int,
    @DiffMerge val obj: Obj
) {
    data class Obj(
        val name: String,
        @DiffAppend val arr: List<Int>
    )
}
