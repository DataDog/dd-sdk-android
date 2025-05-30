/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import com.datadog.benchmark.sample.config.BenchmarkConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BenchmarkConfigHolder @Inject constructor() {
    var config: BenchmarkConfig? = null
}
