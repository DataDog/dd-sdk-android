/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.display

import com.datadog.android.rum.internal.domain.InfoData

/**
 * Provides information about the display state.
 *
 * @property screenBrightness The current screen brightness,
 * normalized as a float between 0.0 (darkest) and 1.0 (brightest).
 */
internal data class DisplayInfo(
    val screenBrightness: Number? = null
) : InfoData
