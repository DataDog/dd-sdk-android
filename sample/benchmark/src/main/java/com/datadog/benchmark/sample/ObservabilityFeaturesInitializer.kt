/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.content.Intent
import com.datadog.benchmark.sample.config.BenchmarkConfig

/**
 * Interface for initializing observability features based on the benchmark configuration.
 */
internal interface ObservabilityFeaturesInitializer {
    /**
     * Initialize observability features based on the provided configuration and intent.
     *
     * @param config The benchmark configuration that determines which features to enable.
     * @param intent The intent containing synthetic test attributes.
     */
    fun initialize(config: BenchmarkConfig, intent: Intent)
}
