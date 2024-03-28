/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.viewpager

import androidx.core.os.bundleOf

internal class FragmentB : PagerChildFragment() {

    companion object {
        fun newInstance(): FragmentB {
            return FragmentB().apply {
                arguments = bundleOf(
                    ARG_PAGE_NAME to "Fragment B",
                    ARG_WEB_VIEW_URL to "https://datadoghq.dev/browser-sdk-test-playground/webview-support/",
                    "fragmentClassName" to FragmentB::class.java.simpleName
                )
            }
        }
    }
}
