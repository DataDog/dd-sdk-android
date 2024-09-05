/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.sessionreplay

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sample.R
import java.util.concurrent.atomic.AtomicInteger

internal interface ImageLoadedCallback {
    fun onImageLoaded(resource: Drawable)
}

internal class ImageComponentsFragment : Fragment() {
    private lateinit var viewModel: ImageComponentsViewModel
    private lateinit var textViewRemote: TextView
    private lateinit var buttonRemote: Button
    private lateinit var imageViewRemote: ImageView
    private lateinit var imageButtonRemote: ImageButton
    private lateinit var appCompatButtonRemote: AppCompatImageButton

    @Suppress("MagicNumber")
    private val imageLoadedCounter: AtomicInteger = AtomicInteger(4)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_image_components, container, false)
        textViewRemote = rootView.findViewById(R.id.textViewRemote)
        buttonRemote = rootView.findViewById(R.id.buttonRemote)
        imageViewRemote = rootView.findViewById(R.id.imageView_remote)
        imageButtonRemote = rootView.findViewById(R.id.imageButtonRemote)
        appCompatButtonRemote = rootView.findViewById(R.id.appCompatImageButtonRemote)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(ImageComponentsViewModel::class.java)
        loadRemoteViews()
    }

    // internal region

    private fun loadRemoteViews() {
        loadImageView()
        loadButton()
        loadTextView()
        loadImageButtonBackground()
    }

    private fun loadImageView() {
        viewModel.fetchRemoteImage(
            LARGE_IMAGE_URL,
            imageViewRemote,
            object : ImageLoadedCallback {
                override fun onImageLoaded(resource: Drawable) {
                    imageViewRemote.setImageDrawable(resource)
                    decrementLoadingCounter()
                }
            }
        )
    }

    private fun loadButton() {
        viewModel.fetchRemoteImage(
            SMALL_IMAGE_URL,
            buttonRemote,
            object : ImageLoadedCallback {
                override fun onImageLoaded(resource: Drawable) {
                    buttonRemote.setCompoundDrawablesWithIntrinsicBounds(null, null, null, resource)
                    decrementLoadingCounter()
                }
            }
        )
    }

    private fun loadTextView() {
        viewModel.fetchRemoteImage(
            SMALL_IMAGE_URL,
            textViewRemote,
            object : ImageLoadedCallback {
                override fun onImageLoaded(resource: Drawable) {
                    textViewRemote.setCompoundDrawablesWithIntrinsicBounds(null, null, null, resource)
                    decrementLoadingCounter()
                }
            }
        )
    }

    private fun loadImageButtonBackground() {
        viewModel.fetchRemoteImage(
            LARGE_IMAGE_URL,
            imageButtonRemote,
            object : ImageLoadedCallback {
                override fun onImageLoaded(resource: Drawable) {
                    imageButtonRemote.background = resource
                    appCompatButtonRemote.background = resource
                    decrementLoadingCounter()
                }
            }
        )
    }

    @OptIn(ExperimentalRumApi::class)
    private fun decrementLoadingCounter() {
        if (imageLoadedCounter.decrementAndGet() == 0) {
            GlobalRumMonitor.get().addViewLoadingTime()
        }
    }

    // endregion

    companion object {
        private const val SMALL_IMAGE_URL = "https://picsum.photos/100/100"
        private const val LARGE_IMAGE_URL = "https://picsum.photos/800/450"
    }
}
