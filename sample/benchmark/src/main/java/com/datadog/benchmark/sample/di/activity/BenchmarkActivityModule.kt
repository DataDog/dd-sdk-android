/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.activity

import com.datadog.benchmark.sample.navigation.FragmentsNavigationManager
import com.datadog.benchmark.sample.navigation.FragmentsNavigationManagerImpl
import dagger.Binds
import dagger.Module

@Module
internal interface BenchmarkActivityModule {
    @Binds
    @BenchmarkActivityScope
    fun bindFragmentsNavigationManager(impl: FragmentsNavigationManagerImpl): FragmentsNavigationManager
}
