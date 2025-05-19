/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.utils

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

internal fun <C : Any> Fragment.componentHolderViewModel(factory: ViewModel.() -> C): Lazy<C> {
    return lazy {
        viewModels<ComponentHolderViewModel<C>> {
            viewModelFactory {
                initializer {
                    ComponentHolderViewModel<C>().apply {
                        component = factory()
                    }
                }
            }
        }.value.component
    }
}

private class ComponentHolderViewModel<C : Any> : ViewModel() {
    lateinit var component: C
}
