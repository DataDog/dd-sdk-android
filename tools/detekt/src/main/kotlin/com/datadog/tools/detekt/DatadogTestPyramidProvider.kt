/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt

import com.datadog.tools.detekt.rules.ApiSurface
import com.datadog.tools.detekt.rules.ApiUsage
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * The [RuleSetProvider] for Datadog's SDK for Android.
 */
class DatadogTestPyramidProvider : RuleSetProvider {

    override val ruleSetId: String = "datadog-test-pyramid"

    override fun instance(config: Config): RuleSet {
        return RuleSet(
            ruleSetId,
            listOf(
                ApiUsage(config),
                ApiSurface(config)
            )
        )
    }
}
