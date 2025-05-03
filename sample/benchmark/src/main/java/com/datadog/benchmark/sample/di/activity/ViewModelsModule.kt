/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.activity

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.datadog.android.api.SdkCore
import com.datadog.android.log.Logger
import com.datadog.benchmark.sample.ui.logscustom.LogsScreenViewModel
import com.datadog.benchmark.sample.ui.logsheavytraffic.LogsHeavyTrafficViewModel
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import kotlin.random.Random
import kotlin.reflect.KClass

// TODO WAHAHA maybe rename
@Qualifier
internal annotation class ViewModel(val viewModelType: KClass<*>)

@Module
internal interface ViewModelsModule {
    companion object {
        @Provides
        @ViewModel(LogsScreenViewModel::class)
        fun provideLogsScreenViewModelFactory(
            logger: Logger
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LogsScreenViewModel(
                    logger = logger,
                    defaultDispatcher = Dispatchers.Default
                )
            }
        }
    }
}
