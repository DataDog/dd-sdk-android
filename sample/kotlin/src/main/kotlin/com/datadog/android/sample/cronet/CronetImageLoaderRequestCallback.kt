/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.cronet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels

internal abstract class CronetImageLoaderRequestCallback : UrlRequest.Callback() {
    private val receivedData = ByteArrayOutputStream()

    override fun onRedirectReceived(
        request: UrlRequest,
        info: UrlResponseInfo,
        newLocationUrl: String
    ) {
        request.followRedirect()
    }

    override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
        request.read(ByteBuffer.allocateDirect(BUFFER_SIZE_100_KB))
    }

    @Suppress("TooGenericExceptionCaught")
    override fun onReadCompleted(
        request: UrlRequest,
        info: UrlResponseInfo,
        byteBuffer: ByteBuffer
    ) {
        byteBuffer.flip()
        try {
            Channels.newChannel(receivedData).write(byteBuffer)
        } catch (e: Exception) {
            Timber.e(e)
        }
        byteBuffer.clear()
        request.read(byteBuffer)
    }

    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
        val imageData = receivedData.toByteArray()
        onBitmapLoaded(BitmapFactory.decodeByteArray(imageData, 0, imageData.size))
    }

    abstract fun onBitmapLoaded(bitmap: Bitmap)

    companion object {
        private const val BUFFER_SIZE_100_KB = 102400
    }
}
