/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class GitHashValueSource @Inject constructor(
    private val execOperations: ExecOperations
) : ValueSource<String, ValueSourceParameters.None> {

    override fun obtain(): String? {
        return System.getProperty(SDK_COMMIT_SHA1_PROPERTY)
            ?: execOperations.execShell("git", "rev-parse", "HEAD").joinToString().trim()
    }

    private companion object {
        const val SDK_COMMIT_SHA1_PROPERTY = "sdkCommitSha1"
    }
}
