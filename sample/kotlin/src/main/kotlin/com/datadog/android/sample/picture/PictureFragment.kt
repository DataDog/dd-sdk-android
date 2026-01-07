/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.picture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.datadog.android.sample.Preferences
import com.datadog.android.sample.R

internal class PictureFragment :
    Fragment(), View.OnClickListener {

    private lateinit var picture: ImageView
    private lateinit var rootView: View
    private lateinit var viewModel: PictureViewModel

    // region Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootView = inflater.inflate(R.layout.fragment_picture, container, false)
        picture = rootView.findViewById(R.id.picture)
        rootView.findViewById<View>(R.id.load_picture).setOnClickListener(this)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(PictureViewModel::class.java)
        context?.let {
            viewModel.selectImageLoader(
                Preferences.defaultPreferences(it).getImageLoader()
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val currentType = viewModel.getImageLoader()
        inflater.inflate(R.menu.image_loader, menu)
        val disabled = when (currentType) {
            ImageLoaderType.COIL -> R.id.image_loader_coil
            ImageLoaderType.COIL3 -> R.id.image_loader_coil3
            ImageLoaderType.FRESCO -> R.id.image_loader_fresco
            ImageLoaderType.GLIDE -> R.id.image_loader_glide
            ImageLoaderType.PICASSO -> R.id.image_loader_picasso
        }
        menu.findItem(disabled).isEnabled = false
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val type = when (item.itemId) {
            R.id.image_loader_coil -> ImageLoaderType.COIL
            R.id.image_loader_coil3 -> ImageLoaderType.COIL3
            R.id.image_loader_fresco -> ImageLoaderType.FRESCO
            R.id.image_loader_glide -> ImageLoaderType.GLIDE
            R.id.image_loader_picasso -> ImageLoaderType.PICASSO
            else -> null
        }
        return if (type == null) {
            super.onOptionsItemSelected(item)
        } else {
            viewModel.selectImageLoader(type)
            activity?.invalidateOptionsMenu()
            context?.let {
                Preferences.defaultPreferences(it).setImageLoader(type)
            }
            true
        }
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        if (v.id == R.id.load_picture) {
            viewModel.loadPictureInto(picture)
        }
    }

    // endregion
}
