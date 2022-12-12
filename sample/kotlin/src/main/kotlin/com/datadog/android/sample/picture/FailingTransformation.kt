/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.picture

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import java.security.MessageDigest
import java.security.SecureRandom

@Suppress("MagicNumber")
internal class FailingTransformation : BitmapTransformation() {

    private val random = SecureRandom()

    // region  BitmapTransformation

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        if (random.nextInt() % 10 == 0) {
            error("Unable to apply ${javaClass.simpleName}")
        }
        return TransformationUtils.fitCenter(pool, toTransform, outWidth, outHeight)
    }

    // endregion

    companion object {
        private val ID = FailingTransformation::class.java.canonicalName
            ?: FailingTransformation::class.java.simpleName
        private val ID_BYTES = ID.toByteArray(Charsets.UTF_8)
    }
}
