/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark

open class ReviewBenchmarkExtension {

    internal val thresholds = mutableMapOf<String, Long>()
    internal val ignored = mutableSetOf<String>()

    fun addThreshold(name: String, threshold: Long) {
        thresholds[name] = threshold
    }

    fun ignoreTest(name: String) {
        ignored.add(name)
    }
}
