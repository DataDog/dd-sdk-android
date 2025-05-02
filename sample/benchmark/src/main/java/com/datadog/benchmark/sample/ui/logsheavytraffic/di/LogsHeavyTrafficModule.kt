/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logsheavytraffic.di

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.datadog.android.log.Logger
import com.datadog.benchmark.sample.di.activity.ViewModel
import com.datadog.benchmark.sample.ui.logsheavytraffic.LogsHeavyTrafficViewModel
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.Dispatchers

@Module
internal interface LogsHeavyTrafficModule {
//    companion object {
//        @Provides
//        @ViewModel(LogsHeavyTrafficViewModel::class)
//        fun provideLogsHeavyTrafficViewModelFactory(
//            logger: Logger
//        ) = viewModelFactory {
//            initializer {
//                LogsHeavyTrafficViewModel(
//                    logger = logger,
//                    defaultDispatcher = Dispatchers.Default
//                )
//            }
//        }
//    }
}
