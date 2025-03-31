/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.view.View

/**
 * Represents the result of locating a target view in response to a user interaction,
 * such as a tap or scroll event in [GesturesListener].
 *
 * @property view The Android [View] that was found. If non-null, indicates a classic View was located.
 * @property tag The semantics tag associated with a Jetpack Compose component. If non-null, indicates
 *               that a Compose node with the given semantics tag was found.
 *
 * Only one of [view] or [tag] is expected to be non-null, depending on the UI framework used.
 */
data class ViewTarget(
    val view: View? = null,
    val tag: String? = null
)
