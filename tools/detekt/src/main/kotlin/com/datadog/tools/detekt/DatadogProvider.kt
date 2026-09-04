/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt

import com.datadog.tools.detekt.rules.sdk.CheckInternal
import com.datadog.tools.detekt.rules.sdk.InvalidStringFormat
import com.datadog.tools.detekt.rules.sdk.PackageNameVisibility
import com.datadog.tools.detekt.rules.sdk.PreferTimeProvider
import com.datadog.tools.detekt.rules.sdk.RequireInternal
import com.datadog.tools.detekt.rules.sdk.ThreadSafety
import com.datadog.tools.detekt.rules.sdk.ThrowingInternalException
import com.datadog.tools.detekt.rules.sdk.TodoWithoutTask
import com.datadog.tools.detekt.rules.sdk.UnsafeCallOnNullableType
import com.datadog.tools.detekt.rules.sdk.UnsafeThirdPartyFunctionCall
import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * The [RuleSetProvider] for Datadog's SDK for Android.
 */
class DatadogProvider : RuleSetProvider {

    override val ruleSetId: RuleSetId = RuleSetId("datadog")

    override fun instance(): RuleSet {
        return RuleSet(
            ruleSetId,
            listOf<(Config) -> Rule>(
                ::CheckInternal,
                ::InvalidStringFormat,
                ::PackageNameVisibility,
                ::PreferTimeProvider,
                ::RequireInternal,
                ::ThreadSafety,
                ::ThrowingInternalException,
                ::TodoWithoutTask,
                ::UnsafeCallOnNullableType,
                ::UnsafeThirdPartyFunctionCall
            )
        )
    }
}
