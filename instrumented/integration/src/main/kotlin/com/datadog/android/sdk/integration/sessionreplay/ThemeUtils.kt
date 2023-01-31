/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.content.res.Resources.Theme
import android.util.TypedValue

internal object ThemeUtils {

    fun resolveThemeColor(theme: Theme): Int? {
        val a = TypedValue()
        theme.resolveAttribute(android.R.attr.windowBackground, a, true)
        return if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            a.type <= TypedValue.TYPE_LAST_COLOR_INT
        ) {
            // windowBackground is a color
            a.data
        } else {
            null
        }
    }
}
