/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt

import com.datadog.tools.detekt.rules.CheckInternal
import com.datadog.tools.detekt.rules.InvalidStringFormat
import com.datadog.tools.detekt.rules.RequireInternal
import com.datadog.tools.detekt.rules.ThreadSafety
import com.datadog.tools.detekt.rules.ThrowingInternalException
import com.datadog.tools.detekt.rules.TodoWithoutTask
import com.datadog.tools.detekt.rules.UnsafeCallOnNullableType
import com.datadog.tools.detekt.rules.UnsafeThirdPartyFunctionCall
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * The [RuleSetProvider] for Datadog's SDK for Android.
 */
class DatadogProvider : RuleSetProvider {

    override val ruleSetId: String = "datadog"

    override fun instance(config: Config): RuleSet {
        return RuleSet(
            ruleSetId,
            listOf(
                CheckInternal(),
                InvalidStringFormat(),
                RequireInternal(),
                ThreadSafety(),
                ThrowingInternalException(),
                TodoWithoutTask(),
                UnsafeCallOnNullableType(),
                UnsafeThirdPartyFunctionCall(config)
            )
        )
    }
}
