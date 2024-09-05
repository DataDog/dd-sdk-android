/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.sample.R
import com.datadog.android.sample.SynchronousLoadedFragment

internal class AboutFragment :
    SynchronousLoadedFragment() {

    private lateinit var viewModel: AboutViewModel
    private lateinit var aboutText: TextView
    private lateinit var licenseText: TextView

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_about, container, false)

        aboutText = rootView.findViewById(R.id.about_text)
        licenseText = rootView.findViewById(R.id.license_text)

        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(AboutViewModel::class.java)
    }

    override fun onResume() {
        super.onResume()
        val currentContext = context ?: return
        viewModel.getAboutText(currentContext) {
            aboutText.text = it
        }
        viewModel.getLicenseText(currentContext) {
            licenseText.text = it
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopAsyncOperations()
    }

    // endregion
}
