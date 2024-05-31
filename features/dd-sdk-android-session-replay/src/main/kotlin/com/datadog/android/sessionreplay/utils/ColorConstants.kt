/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

internal const val ALPHA_SHIFT_ANDROID = 24
internal const val ALPHA_SHIFT_WEB = 8

internal const val MAX_ALPHA_VALUE = 0xFF
internal const val WEB_COLOR_STR_LENGTH = 8

internal const val MASK_ALPHA = 0xFF000000L
internal const val MASK_RGB = 0x00FFFFFFL
internal const val MASK_COLOR = 0xFFFFFFFFL

/** The value corresponding to an opaque Alpha. */
const val OPAQUE_ALPHA_VALUE: Int = MAX_ALPHA_VALUE

/** The value corresponding to a 25% opaque Alpha. */
const val PARTIALLY_OPAQUE_ALPHA_VALUE: Int = 0x40
