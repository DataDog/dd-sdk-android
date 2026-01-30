/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.activity

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.datadog.android.log.Logger
import com.datadog.benchmark.sample.observability.ObservabilityRumMonitor
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherQualifier
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherType
import com.datadog.benchmark.sample.observability.ObservabilityLogger
import com.datadog.benchmark.sample.ui.logscustom.LogsScreenViewModel
import com.datadog.benchmark.sample.ui.rummanual.RumManualScenarioViewModel
import com.datadog.benchmark.sample.ui.trace.TraceScenarioViewModel
import dagger.Module
import dagger.Provides
import com.datadog.benchmark.sample.observability.ObservabilityTracer
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Qualifier
import kotlin.reflect.KClass

@Qualifier
internal annotation class ViewModelQualifier(val viewModelType: KClass<*>)

@Module
internal interface ViewModelsModule {
    companion object {
        @Provides
        @ViewModelQualifier(LogsScreenViewModel::class)
        fun provideLogsScreenViewModelFactory(
            logger: ObservabilityLogger,
            @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
            defaultDispatcher: CoroutineDispatcher
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LogsScreenViewModel(
                    logger = logger,
                    defaultDispatcher = defaultDispatcher
                )
            }
        }

        @Provides
        @ViewModelQualifier(TraceScenarioViewModel::class)
        fun provideTraceScenarioViewModelFactory(
            tracer: ObservabilityTracer,
            @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
            defaultDispatcher: CoroutineDispatcher
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TraceScenarioViewModel(
                    tracer = tracer,
                    defaultDispatcher = defaultDispatcher
                )
            }
        }

        @Provides
        @ViewModelQualifier(RumManualScenarioViewModel::class)
        fun provideRumManualScenarioViewModelFactory(
            rumMonitor: ObservabilityRumMonitor,
            @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
            defaultDispatcher: CoroutineDispatcher
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RumManualScenarioViewModel(
                    rumMonitor = rumMonitor,
                    defaultDispatcher = defaultDispatcher
                )
            }
        }
    }
}
