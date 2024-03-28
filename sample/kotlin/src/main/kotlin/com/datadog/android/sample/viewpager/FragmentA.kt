/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.viewpager

import androidx.core.os.bundleOf

internal class FragmentA : PagerChildFragment() {

    companion object {
        fun newInstance(): FragmentA {
            return FragmentA().apply {
                arguments = bundleOf(
                    ARG_PAGE_NAME to "Fragment A",
                    ARG_WEB_VIEW_URL to "https://datadoghq.dev/browser-sdk-test-playground/webview-support/",
                    "fragmentClassName" to FragmentA::class.java.simpleName
                )
            }
        }
    }
}
