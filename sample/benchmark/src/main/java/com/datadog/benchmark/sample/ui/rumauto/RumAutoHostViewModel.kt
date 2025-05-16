/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto

import com.datadog.benchmark.sample.ui.rumauto.di.RumAutoScenarioScope
import javax.inject.Inject

internal enum class RumAutoScenarioTab {
    CHARACTERS,
    EPISODES,
    LOCATIONS,
    DOCS
}

internal sealed interface RumAutoScenarioAction {
    data class OnTabClicked(val tab: RumAutoScenarioTab): RumAutoScenarioAction
}

@RumAutoScenarioScope
internal class RumAutoHostViewModel @Inject constructor(
    private val navigator: RumAutoScenarioNavigator,
) {
    fun dispatch(action: RumAutoScenarioAction) {
        when (action) {
            is RumAutoScenarioAction.OnTabClicked -> navigator.openTab(action.tab)
        }
    }
}
