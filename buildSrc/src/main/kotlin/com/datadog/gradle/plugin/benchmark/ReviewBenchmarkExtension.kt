/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark

open class ReviewBenchmarkExtension {

    internal val benchmarkStrategies = mutableMapOf<String, BenchmarkStrategy>()
    fun addThreshold(name: String, threshold: Long) {
        benchmarkStrategies[name] = BenchmarkStrategy.AbsoluteThreshold(name, threshold)
    }

    fun ignoreTest(name: String) {
        benchmarkStrategies[name] = BenchmarkStrategy.Ignore
    }

    fun relativeThreshold(betweenTest: String, andTest: String, shouldNotExceed: Long) {
        benchmarkStrategies[betweenTest] =
            BenchmarkStrategy.RelativeThreshold(betweenTest, andTest, shouldNotExceed)
        // add an ignore strategy for the second in order to not be matched
        // as a no - strategy benchmark
        benchmarkStrategies[andTest] = BenchmarkStrategy.Ignore
    }
}
