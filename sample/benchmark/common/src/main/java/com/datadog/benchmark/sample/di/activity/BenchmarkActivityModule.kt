/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.activity

import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayNavigationManager
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayNavigationManagerImpl
import dagger.Binds
import dagger.Module

@Module
interface BenchmarkActivityModule {
    @Binds
    @BenchmarkActivityScope
    fun bindSessionReplayNavigationManager(impl: SessionReplayNavigationManagerImpl): SessionReplayNavigationManager
}
