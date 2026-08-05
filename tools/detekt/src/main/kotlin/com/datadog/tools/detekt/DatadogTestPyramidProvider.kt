/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt

import com.datadog.tools.detekt.rules.pyramid.ApiSurface
import com.datadog.tools.detekt.rules.pyramid.ApiUsage
import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * The [RuleSetProvider] for Datadog's SDK for Android.
 */
class DatadogTestPyramidProvider : RuleSetProvider {

    override val ruleSetId: RuleSetId = RuleSetId("datadog-test-pyramid")

    override fun instance(): RuleSet {
        return RuleSet(
            ruleSetId,
            listOf<(Config) -> Rule>(
                ::ApiUsage,
                ::ApiSurface
            )
        )
    }
}
