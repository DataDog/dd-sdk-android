/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

@Suppress("unused", "UndocumentedPublicClass") // used only by Lint tool
class DatadogIssueRegistry : IssueRegistry() {

    override val api = CURRENT_API

    // probably works with even lower APIs, but it needs to be checked
    override val minApi = ANDROID_STUDIO_8_API

    override val issues = listOf(
        InternalApiUsageDetector.ISSUE
    )

    override val vendor = Vendor(
        vendorName = "Datadog",
        feedbackUrl = "https://github.com/DataDog/dd-sdk-android/"
    )

    private companion object {
        const val ANDROID_STUDIO_8_API = 14
    }
}
