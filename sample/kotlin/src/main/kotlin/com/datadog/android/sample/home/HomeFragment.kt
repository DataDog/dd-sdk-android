/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.datadog.android.sample.R

class HomeFragment :
    Fragment(),
    View.OnClickListener {

    private lateinit var viewModel: HomeViewModel
    lateinit var navController: NavController

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_home, container, false)
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
        viewModel = ViewModelProviders.of(this).get(HomeViewModel::class.java)
        navController = findNavController(this)
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        val destination = when (v.id) {
            R.id.navigation_logs -> R.id.fragment_logs
            R.id.navigation_crash -> R.id.fragment_crash
            R.id.navigation_traces -> R.id.fragment_trace
            R.id.navigation_vitals -> R.id.fragment_vitals
            R.id.navigation_webview -> R.id.fragment_webview
            R.id.navigation_data_list -> R.id.fragment_data_list
            R.id.navigation_view_pager -> R.id.activity_view_pager
            R.id.navigation_picture -> R.id.fragment_picture
            R.id.navigation_compose -> R.id.activity_jetpack_compose
            R.id.navigation_about -> R.id.fragment_about
            else -> null
        }
        if (destination != null) {
            navController.navigate(destination)
        }
    }

    //endregion
}
