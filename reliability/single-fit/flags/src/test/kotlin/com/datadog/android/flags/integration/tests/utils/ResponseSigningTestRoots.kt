/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration.tests.utils

import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.ResponseSigningMode

/**
 * Opts into response signing with test-only anchors. The public API pins the staging root only, and test
 * credentials cannot chain to it, so this sets the internal test override through reflection.
 */
internal fun FlagsConfiguration.Builder.responseSigningWithTestRoots(
    mode: ResponseSigningMode,
    roots: List<String>
): FlagsConfiguration.Builder {
    responseSigning(mode)
    val configuration = Class.forName("com.datadog.android.flags.internal.signing.ResponseSigningConfiguration")
        .getDeclaredConstructor(ResponseSigningMode::class.java, List::class.java)
        .newInstance(mode, roots.toList())
    FlagsConfiguration.Builder::class.java.getDeclaredField("responseSigning").apply {
        isAccessible = true
        set(this@responseSigningWithTestRoots, configuration)
    }
    return this
}
