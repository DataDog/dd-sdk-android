/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

/**
 * The one Logcat tag shared by every one-time diagnostic confirmation in the experimental
 * pixel-capture pipeline — [PixelCapture] and [CompositionTreeBuilder] both log through this
 * single constant, so filtering Logcat by one tag surfaces every confirmation from both. Each
 * message still carries its own bracketed component prefix (e.g. `[PixelCapture]`,
 * `[CompositionTree]`) to stay distinguishable once filtered.
 */
internal const val CAPTURE_PIPELINE_LOG_TAG = "DD_SessionReplay"
