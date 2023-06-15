/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.sessionreplay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.datadog.android.sample.R

internal class SessionReplayFragment :
    Fragment(),
    View.OnClickListener {

    lateinit var navController: NavController

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_session_replay, container, false)
        if (rootView is ViewGroup) {
            val constraintLayout = rootView.children.filterIsInstance<ConstraintLayout>()
                .firstOrNull()
            constraintLayout?.children?.filterIsInstance<Button>()
                ?.forEach { it.setOnClickListener(this) }
        }
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        navController = findNavController(this)
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        val destination = when (v.id) {
            R.id.navigation_picker_components -> R.id.fragment_picker_components
            R.id.navigation_text_view_components -> R.id.fragment_text_view_components
            R.id.navigation_radio_and_checkbox_components -> R.id.fragment_radio_checkbox_components
            R.id.navigation_dropdowns_and_switchers_components ->
                R.id.fragment_dropdowns_switchers_components
            R.id.navigation_sliders_and_steppers_components -> R.id.fragment_sliders_steppers_components
            R.id.navigation_different_fonts -> R.id.fragment_different_fonts
            R.id.navigation_password_edit_text_components -> R.id.fragment_password_edit_text_components
            R.id.navigation_unsupported_views -> R.id.fragment_unsupported_views
            R.id.navigation_image_components -> R.id.fragment_image_components

            else -> null
        }
        if (destination != null) {
            navController.navigate(destination)
        }
    }

    //endregion
}
