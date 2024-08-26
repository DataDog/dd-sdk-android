/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal.reader

internal interface VitalReader {
    fun readVitalData(): Double?

    fun start() {
        // do nothing by default
    }

    fun unit(): String?

    fun metricName(): String

    fun stop() {
        // do nothing by default
    }
}
