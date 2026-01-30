/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.common

import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

enum class CoroutineDispatcherType {
    IO, Default, Main
}

@Qualifier
internal annotation class CoroutineDispatcherQualifier(val type: CoroutineDispatcherType)

@Module
object DispatchersModule {
    @Provides
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Main)
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.IO)
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO
}
