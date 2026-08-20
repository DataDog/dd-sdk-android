/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.lint.InternalApi
import com.datadog.android.sessionreplay.ExtensionSupport
import com.datadog.android.sessionreplay.recorder.privacy.TextDetector

/**
 * Optional SDK-internal capability for extensions that find visible text in pixel captures, so the
 * composition-tree pipeline can mask it before upload. Kept separate from [ExtensionSupport] so
 * existing extension binaries remain compatible. Members carry [InternalApi] so public extension
 * implementations remain usable. When no extension provides one, any pixel capture that would need
 * text masking fails closed to a placeholder instead of ever uploading an unverified bitmap.
 */
@Suppress("PackageNameVisibility")
interface TextDetectorProvider : ExtensionSupport {

    /** Returns the extension's text detector, or null if unavailable. */
    @InternalApi
    fun getTextDetector(): TextDetector?
}
