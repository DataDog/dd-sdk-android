/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.sessionreplay

/**
 * Image dimension threshold in DP for determining if an image should be masked.
 * Images >= this size are considered content/PII when using MASK_LARGE_ONLY privacy mode.
 * Material design icon size is up to 48x48, but 100dp is used to match more images.
 */
const val IMAGE_DIMEN_CONSIDERED_PII_IN_DP: Int = 100

/**
 * Meta record type (screen properties).
 * @see com.datadog.android.sessionreplay.model.MobileSegment.MobileRecord.MetaRecord
 */
const val RECORD_TYPE_META: Long = 4L

/**
 * Focus record type (focus state changes).
 * @see com.datadog.android.sessionreplay.model.MobileSegment.MobileRecord.FocusRecord
 */
const val RECORD_TYPE_FOCUS: Long = 6L

/**
 * View end record type.
 * @see com.datadog.android.sessionreplay.model.MobileSegment.MobileRecord.ViewEndRecord
 */
const val RECORD_TYPE_VIEW_END: Long = 7L

/**
 * Visual viewport record type.
 * @see com.datadog.android.sessionreplay.model.MobileSegment.MobileRecord.VisualViewportRecord
 */
const val RECORD_TYPE_VISUAL_VIEWPORT: Long = 8L

/**
 * Full snapshot record type (complete view hierarchy).
 * @see com.datadog.android.sessionreplay.model.MobileSegment.MobileRecord.MobileFullSnapshotRecord
 */
const val RECORD_TYPE_FULL_SNAPSHOT: Long = 10L

/**
 * Incremental snapshot record type (view hierarchy changes, mutations, touch events).
 * @see com.datadog.android.sessionreplay.model.MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord
 */
const val RECORD_TYPE_INCREMENTAL_SNAPSHOT: Long = 11L

/**
 * Shape wireframe type.
 * @see com.datadog.android.sessionreplay.model.MobileSegment.Wireframe.ShapeWireframe
 */
const val WIREFRAME_TYPE_SHAPE: String = "shape"

/**
 * Text wireframe type.
 * @see com.datadog.android.sessionreplay.model.MobileSegment.Wireframe.TextWireframe
 */
const val WIREFRAME_TYPE_TEXT: String = "text"

/**
 * Image wireframe type.
 * @see com.datadog.android.sessionreplay.model.MobileSegment.Wireframe.ImageWireframe
 */
const val WIREFRAME_TYPE_IMAGE: String = "image"

/**
 * Placeholder wireframe type (masked content).
 * @see com.datadog.android.sessionreplay.model.MobileSegment.Wireframe.PlaceholderWireframe
 */
const val WIREFRAME_TYPE_PLACEHOLDER: String = "placeholder"

/**
 * Webview wireframe type.
 * @see com.datadog.android.sessionreplay.model.MobileSegment.Wireframe.WebviewWireframe
 */
const val WIREFRAME_TYPE_WEBVIEW: String = "webview"
