/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.sessionreplay

import android.content.Context
import android.util.AttributeSet
import android.widget.Toolbar
import androidx.appcompat.widget.Toolbar as AppCompatToolbar
import androidx.fragment.app.Fragment
import com.datadog.android.sample.R

internal class UnsupportedViewsFragment : Fragment(R.layout.fragment_unsupported_views)

internal class AppcompatToolbarCustomSubclass(context: Context, attrs: AttributeSet) : AppCompatToolbar(context, attrs)
internal class ToolbarCustomSubclass(context: Context, attrs: AttributeSet) : Toolbar(context, attrs)
