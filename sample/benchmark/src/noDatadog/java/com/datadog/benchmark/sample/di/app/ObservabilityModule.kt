/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import com.datadog.benchmark.sample.observability.ObservabilityLogger
import dagger.Module
import dagger.Provides

@Module
internal interface ObservabilityModule {

    companion object {
        @Provides
        fun provideLogger(): ObservabilityLogger {
            return object : ObservabilityLogger {
                override fun log(
                    priority: Int,
                    message: String,
                    throwable: Throwable?,
                    attributes: Map<String, Any?>
                ) {
                    // no-op
                }
            }
        }
    }
}
