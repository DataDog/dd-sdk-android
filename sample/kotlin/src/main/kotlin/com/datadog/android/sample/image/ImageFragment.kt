/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.image

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.datadog.android.sample.Preferences
import com.datadog.android.sample.R

internal class ImageFragment :
    Fragment(), View.OnClickListener {

    private lateinit var image: ImageView
    private lateinit var frescoImage: ImageView
    private lateinit var rootView: View
    private lateinit var viewModel: ImageViewModel

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootView = inflater.inflate(R.layout.fragment_image, container, false)
        image = rootView.findViewById(R.id.image)
        frescoImage = rootView.findViewById(R.id.image_fresco)
        rootView.findViewById<View>(R.id.load_image).setOnClickListener(this)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ImageViewModel::class.java]
        context?.let {
            val loaderType = Preferences.defaultPreferences(it).getImageLoader()
            viewModel.selectImageLoader(loaderType)
            updateImageViewVisibility(loaderType == ImageLoaderType.FRESCO)
        }
        requireActivity().addMenuProvider(imageLoaderMenuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        if (v.id == R.id.load_image) {
            val isFresco = viewModel.getImageLoader() == ImageLoaderType.FRESCO
            updateImageViewVisibility(isFresco)
            val activeImage = if (isFresco) frescoImage else image
            viewModel.loadImageInto(activeImage)
        }
    }

    // endregion

    // region Internal

    private val imageLoaderMenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            val currentType = viewModel.getImageLoader()
            menuInflater.inflate(R.menu.image_loader, menu)
            val disabled = when (currentType) {
                ImageLoaderType.COIL -> R.id.image_loader_coil
                ImageLoaderType.COIL3 -> R.id.image_loader_coil3
                ImageLoaderType.FRESCO -> R.id.image_loader_fresco
                ImageLoaderType.GLIDE -> R.id.image_loader_glide
                ImageLoaderType.PICASSO -> R.id.image_loader_picasso
            }
            menu.findItem(disabled).isEnabled = false
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            val type = when (menuItem.itemId) {
                R.id.image_loader_coil -> ImageLoaderType.COIL
                R.id.image_loader_coil3 -> ImageLoaderType.COIL3
                R.id.image_loader_fresco -> ImageLoaderType.FRESCO
                R.id.image_loader_glide -> ImageLoaderType.GLIDE
                R.id.image_loader_picasso -> ImageLoaderType.PICASSO
                else -> null
            }
            return if (type != null) {
                viewModel.selectImageLoader(type)
                activity?.invalidateOptionsMenu()
                context?.let {
                    Preferences.defaultPreferences(it).setImageLoader(type)
                }
                true
            } else {
                false
            }
        }
    }

    private fun updateImageViewVisibility(isFresco: Boolean) {
        image.visibility = if (isFresco) View.GONE else View.VISIBLE
        frescoImage.visibility = if (isFresco) View.VISIBLE else View.GONE
    }

    // endregion
}
