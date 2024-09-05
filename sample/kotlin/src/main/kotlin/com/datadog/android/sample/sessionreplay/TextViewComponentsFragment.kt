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
import android.widget.CheckedTextView
import androidx.appcompat.widget.AppCompatCheckedTextView
import com.datadog.android.sample.R
import com.datadog.android.sample.SynchronousLoadedFragment

internal class TextViewComponentsFragment : SynchronousLoadedFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_text_view_components, container, false).apply {
            findViewById<CheckedTextView>(R.id.checked_text_view).apply {
                setOnClickListener { this.toggle() }
            }
            findViewById<AppCompatCheckedTextView>(R.id.app_compat_checked_text_view).apply {
                setOnClickListener { this.toggle() }
            }
        }
    }
}
