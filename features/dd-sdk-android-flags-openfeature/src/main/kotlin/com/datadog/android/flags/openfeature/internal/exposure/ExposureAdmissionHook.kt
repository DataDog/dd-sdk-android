/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.exposure

import dev.openfeature.kotlin.sdk.FlagEvaluationDetails
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.HookContext

/**
 * Records and sends the exposure of a successful evaluation. As a provider hook it runs first in the
 * final stage, after every after hook, so a failing hook prevents the exposure.
 */
internal class ExposureAdmissionHook(private val decisions: ExposureDecisions) : Hook<Any> {
    override fun finallyAfter(ctx: HookContext<Any>, details: FlagEvaluationDetails<Any>, hints: Map<String, Any>) {
        decisions.complete(details)
    }
}
