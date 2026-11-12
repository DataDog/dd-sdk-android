/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Provides the commit SHA embedded in the SDK. The real SHA is only passed (via `-DsdkCommitSha1`) by the CI jobs
 * that ship artifacts; everywhere else a constant stub is used, so the value doesn't invalidate the build cache.
 */
abstract class GitHashValueSource : ValueSource<String, ValueSourceParameters.None> {

    override fun obtain(): String {
        return System.getProperty(SDK_COMMIT_SHA1_PROPERTY) ?: STUB_COMMIT_SHA1
    }

    private companion object {
        const val SDK_COMMIT_SHA1_PROPERTY = "sdkCommitSha1"
        const val STUB_COMMIT_SHA1 = "0000000000000000000000000000000000000000"
    }
}
