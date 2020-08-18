/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.picture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.sample.R

class PictureFragment :
    Fragment(), View.OnClickListener {

    private lateinit var picture: ImageView
    private lateinit var viewModel: PictureViewModel

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_picture, container, false)
        picture = rootView.findViewById(R.id.picture)
        rootView.findViewById<View>(R.id.load_picture).setOnClickListener(this)
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(PictureViewModel::class.java)
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
