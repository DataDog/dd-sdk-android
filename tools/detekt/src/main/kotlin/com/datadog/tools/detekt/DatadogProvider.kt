/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt

import com.datadog.tools.detekt.rules.sdk.CheckInternal
import com.datadog.tools.detekt.rules.sdk.InvalidStringFormat
import com.datadog.tools.detekt.rules.sdk.PackageNameVisibility
import com.datadog.tools.detekt.rules.sdk.RequireInternal
import com.datadog.tools.detekt.rules.sdk.ThreadSafety
import com.datadog.tools.detekt.rules.sdk.ThrowingInternalException
import com.datadog.tools.detekt.rules.sdk.TodoWithoutTask
import com.datadog.tools.detekt.rules.sdk.UnsafeCallOnNullableType
import com.datadog.tools.detekt.rules.sdk.UnsafeThirdPartyFunctionCall
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
                PackageNameVisibility(config),
                RequireInternal(),
                ThreadSafety(),
                ThrowingInternalException(),
                TodoWithoutTask(config),
                UnsafeCallOnNullableType(),
                UnsafeThirdPartyFunctionCall(config)
            )
        )
    }
}
