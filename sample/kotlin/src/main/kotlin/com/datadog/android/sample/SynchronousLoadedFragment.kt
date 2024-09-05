/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor

internal open class SynchronousLoadedFragment : Fragment {

    constructor() : super()

    constructor(@LayoutRes contentLayoutId: Int) : super(contentLayoutId)

    @OptIn(ExperimentalRumApi::class)
    override fun onResume() {
        super.onResume()
        GlobalRumMonitor.get().addViewLoadingTime()
    }
}
