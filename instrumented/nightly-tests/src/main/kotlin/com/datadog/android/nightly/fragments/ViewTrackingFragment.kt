/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.datadog.android.nightly.R
import com.datadog.android.nightly.utils.measure

internal class ViewTrackingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.tracking_strategy_fragment, container, false)
    }

    override fun onResume() {
        measure(TEST_METHOD_NAME) {
            super.onResume()
        }
    }

    companion object {
        private const val TEST_METHOD_NAME = "rum_fragment_view_tracking_strategy"
    }
}
