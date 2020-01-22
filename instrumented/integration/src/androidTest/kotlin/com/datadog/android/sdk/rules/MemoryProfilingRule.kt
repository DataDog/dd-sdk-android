/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.sdk.rules

internal class MemoryProfilingRule :
    AbstractProfilingRule(name = "Memory usage in Kb") {

    // region AbstractProfilingRule

    override fun measure(): Double {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()).toDouble() / 1024.0
    }

    // endregion
}
