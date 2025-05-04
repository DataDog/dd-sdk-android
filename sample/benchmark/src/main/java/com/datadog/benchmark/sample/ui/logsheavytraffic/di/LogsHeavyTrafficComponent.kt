/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logsheavytraffic.di

import androidx.lifecycle.ViewModelProvider
import com.datadog.android.log.Logger
import dagger.Component
import javax.inject.Scope

internal interface LogsHeavyTrafficComponentDependencies {
    val logger: Logger
}

@Scope
internal annotation class LogsHeavyTrafficScope

@LogsHeavyTrafficScope
@Component(
    dependencies = [LogsHeavyTrafficComponentDependencies::class],
    modules = [LogsHeavyTrafficModule::class]
)
internal interface LogsHeavyTrafficComponent {
    @Component.Factory
    interface Factory {
        fun create(
            deps: LogsHeavyTrafficComponentDependencies
        ): LogsHeavyTrafficComponent
    }

    val viewModelFactory: ViewModelProvider.Factory
}
