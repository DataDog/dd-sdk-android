/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil3.request.crossfade
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.compose.ComposeExtensionSupport
import coil3.ImageLoader as ImageLoader3
import coil3.compose.AsyncImage as AsyncImage3
import coil3.compose.rememberAsyncImagePainter as rememberAsyncImagePainter3
import coil3.request.CachePolicy as CachePolicy3
import coil3.request.ImageRequest as ImageRequest3

/**
 * Shows the same crossfade image with Coil 2 and Coil 3, each through `AsyncImage` and through
 * `Image(rememberAsyncImagePainter(...))`. The memory cache is disabled so that Coil always
 * crossfades instead of displaying a cached result directly.
 */
internal class SessionReplayComposeCoilActivity : BaseSessionReplayActivity() {

    private lateinit var imageLoader: ImageLoader
    private lateinit var imageLoader3: ImageLoader3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bitmap = Bitmap.createBitmap(IMAGE_SIZE_PX, IMAGE_SIZE_PX, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(IMAGE_COLOR)
        imageLoader = ImageLoader.Builder(this).build()
        val imageRequest = ImageRequest.Builder(this)
            .data(bitmap)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .crossfade(CROSSFADE_DURATION_MS)
            .build()
        imageLoader3 = ImageLoader3.Builder(this).build()
        val imageRequest3 = ImageRequest3.Builder(this)
            .data(bitmap)
            .memoryCachePolicy(CachePolicy3.DISABLED)
            .crossfade(CROSSFADE_DURATION_MS)
            .build()

        setContentView(
            ComposeView(this).apply {
                setContent {
                    // Session Replay skips leaf nodes at the origin, so keep the images away from it.
                    Column(modifier = Modifier.padding(IMAGE_PADDING_DP.dp)) {
                        AsyncImage(
                            model = imageRequest,
                            imageLoader = imageLoader,
                            contentDescription = "Coil AsyncImage",
                            modifier = Modifier.size(IMAGE_SIZE_DP.dp)
                        )
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = imageRequest,
                                imageLoader = imageLoader
                            ),
                            contentDescription = "Coil remembered painter",
                            modifier = Modifier.size(IMAGE_SIZE_DP.dp)
                        )
                        AsyncImage3(
                            model = imageRequest3,
                            imageLoader = imageLoader3,
                            contentDescription = "Coil 3 AsyncImage",
                            modifier = Modifier.size(IMAGE_SIZE_DP.dp)
                        )
                        Image(
                            painter = rememberAsyncImagePainter3(
                                model = imageRequest3,
                                imageLoader = imageLoader3
                            ),
                            contentDescription = "Coil 3 remembered painter",
                            modifier = Modifier.size(IMAGE_SIZE_DP.dp)
                        )
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        imageLoader.shutdown()
        imageLoader3.shutdown()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun sessionReplayConfiguration(
        privacy: SessionReplayPrivacy,
        sampleRate: Float
    ): SessionReplayConfiguration {
        val builder = RuntimeConfig.sessionReplayConfigBuilder(sampleRate)
            .setPrivacy(privacy)
            .addExtensionSupport(ComposeExtensionSupport())
        intent.getImagePrivacy()?.let { builder.setImagePrivacy(it) }
        return builder.build()
    }

    companion object {
        private const val IMAGE_SIZE_PX = 64
        private const val IMAGE_SIZE_DP = 80
        private const val IMAGE_PADDING_DP = 16
        private const val IMAGE_COLOR = 0xFF123456.toInt()
        private const val CROSSFADE_DURATION_MS = 1000
    }
}
