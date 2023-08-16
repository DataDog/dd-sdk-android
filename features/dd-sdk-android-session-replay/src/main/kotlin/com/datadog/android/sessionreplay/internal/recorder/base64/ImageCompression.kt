/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Interface for handling image compression formats.
 */
internal interface ImageCompression {

    /**
     * Get the mimetype for the image format.
     */
    fun getMimeType(): String?

    /**
     * Compress the bitmap to a [ByteArrayOutputStream].
     */
    fun compressBitmap(bitmap: Bitmap): ByteArray
}
