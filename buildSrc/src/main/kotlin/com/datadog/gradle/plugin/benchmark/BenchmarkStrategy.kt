/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark

import java.util.concurrent.TimeUnit

sealed class BenchmarkStrategy {

    abstract fun verify(benchmarkResults: Map<String, Long>): Boolean

    class AbsoluteThreshold(val compare: String, val threshold: Long) : BenchmarkStrategy() {
        override fun verify(benchmarkResults: Map<String, Long>): Boolean {
            val toMeasure = getOrThrow(benchmarkResults, compare)
            return when {
                toMeasure <= threshold -> true
                else -> {
                    System.err.println(
                        "Benchmark test \"$$compare\" reported a median time of " +
                            "$toMeasure milliseconds, but threshold is set to " +
                            "$threshold milliseconds"
                    )
                    false
                }
            }
        }
    }

    class RelativeThreshold(
        val compare: String,
        val compareTo: String,
        val threshold: Long
    ) : BenchmarkStrategy() {
        override fun verify(benchmarkResults: Map<String, Long>): Boolean {

            val medianA = getOrThrow(benchmarkResults, compare)
            val medianB = getOrThrow(benchmarkResults, compareTo)
            val toMeasure = Math.abs(medianA - medianB)
            return when {
                toMeasure <= threshold -> true
                else -> {
                    System.err.println(
                        "We were expecting a relativeThreshold smaller or equal with $threshold " +
                            "milliseconds between the benchmark : \"$compare\" and : " +
                            "\"$compareTo\" instead it was of $toMeasure milliseconds"
                    )
                    false
                }
            }
        }
    }

    internal fun getOrThrow(benchmarkResults: Map<String, Long>, key: String): Long {
        val l = benchmarkResults[key]
        checkNotNull(l) {
            System.err.println(
                "There was no benchmark for test \"$key\""
            )
        }
        return l.toMillis()
    }

    object Ignore : BenchmarkStrategy() {
        override fun verify(benchmarkResults: Map<String, Long>): Boolean {
            return true
        }
    }

    private fun Long.toMillis(): Long {
        return TimeUnit.NANOSECONDS.toMillis(this)
    }
}
