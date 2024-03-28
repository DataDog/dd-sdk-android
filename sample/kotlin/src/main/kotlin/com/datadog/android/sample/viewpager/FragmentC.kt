/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.viewpager

import androidx.core.os.bundleOf
import com.datadog.android.sample.BuildConfig

internal class FragmentC : PagerChildFragment() {

    companion object {
        fun newInstance(): FragmentC {
            return FragmentC().apply {
                arguments = bundleOf(
                    ARG_PAGE_NAME to "Fragment C",
                    ARG_WEB_VIEW_URL to
                        "https://datadoghq.dev/browser-sdk-test-playground/" +
                        "?client_token=${BuildConfig.DD_CLIENT_TOKEN}" +
                        "&application_id=${BuildConfig.DD_RUM_APPLICATION_ID}" +
                        "&site=datadoghq.com",
                    "fragmentClassName" to FragmentC::class.java.simpleName
                )
            }
        }
    }
}
