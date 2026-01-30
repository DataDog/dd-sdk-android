/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.cronet

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.datadog.android.cronet.DatadogCronetEngine
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import com.datadog.android.sample.R
import com.datadog.android.trace.NetworkTracingInstrumentation
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.util.concurrent.Executors

internal class CronetImageFragment : Fragment() {

    private lateinit var loadButton: Button
    private lateinit var imageView: ImageView
    private lateinit var cronetEngine: CronetEngine
    private val executor = Executors.newSingleThreadExecutor()

    private var imageIndex = 0
    private val imageUrls = listOf(
        "https://storage.googleapis.com/cronet/sun.jpg",
        "https://storage.googleapis.com/cronet/flower.jpg",
        "https://storage.googleapis.com/cronet/chair.jpg",
        "https://storage.googleapis.com/cronet/404.jpg",
        "https://storage.googleapis.com/cronet/white.jpg",
        "https://storage.googleapis.com/cronet/moka.jpg",
        "https://storage.googleapis.com/cronet/walnut.jpg"
    )

    private val tracedHosts = listOf(
        "datadoghq.com",
        "127.0.0.1",
        "storage.googleapis.com"
    )

    @OptIn(ExperimentalRumApi::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(
        R.layout.fragment_cronet_image,
        container,
        false
    ).also { rootView ->
        imageView = rootView.findViewById(R.id.cronet_image_view)
        loadButton = rootView.findViewById(R.id.cronet_load_button)

        cronetEngine = DatadogCronetEngine.Builder(requireContext())
            .enableQuic(true)
            .enableHttp2(true)
            .enableNetworkTracing(NetworkTracingInstrumentation.Configuration(tracedHosts))
            .setCustomRumInstrumentation(RumResourceInstrumentation.Configuration())
            .build()
        loadButton.setOnClickListener { loadRandomImage() }
    }

    private fun loadRandomImage() {
        val randomUrl = imageUrls[++imageIndex % imageUrls.size]
        loadButton.isEnabled = false

        cronetEngine.newUrlRequestBuilder(
            randomUrl,
            object : CronetImageLoaderRequestCallback() {
                override fun onBitmapLoaded(bitmap: Bitmap) {
                    activity?.runOnUiThread {
                        imageView.setImageBitmap(bitmap)
                        loadButton.isEnabled = true
                    }
                }

                override fun onFailed(
                    request: UrlRequest,
                    info: UrlResponseInfo?,
                    error: CronetException
                ) {
                    activity?.runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            "Failed to load image: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadButton.isEnabled = true
                    }
                }
            },
            executor
        ).build().start()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        cronetEngine.shutdown()
    }
}
