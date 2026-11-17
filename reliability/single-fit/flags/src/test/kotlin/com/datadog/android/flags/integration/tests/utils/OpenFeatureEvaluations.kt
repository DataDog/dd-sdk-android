/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration.tests.utils

import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.FlagEvaluationDetails
import dev.openfeature.kotlin.sdk.FlagValueType
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.HookContext
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.from

/**
 * Evaluates with the provider directly, then runs the provider's final hooks as the OpenFeature client does.
 * The provider records exposures in its final hook.
 */
@Suppress("UNCHECKED_CAST")
internal fun FeatureProvider.getBooleanEvaluationWithHooks(
    key: String,
    defaultValue: Boolean
): ProviderEvaluation<Boolean> {
    val evaluation = getBooleanEvaluation(key, defaultValue, null)
    val hookContext = HookContext(key, FlagValueType.BOOLEAN, defaultValue, null, null, metadata)
    val details = FlagEvaluationDetails.from(evaluation, key)
    hooks.forEach { (it as Hook<Boolean>).finallyAfter(hookContext, details, emptyMap()) }
    return evaluation
}
