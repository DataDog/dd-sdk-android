/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.model.FlagsClientState

internal interface EvaluationContextOutcomeCallback : EvaluationContextCallback {
    fun onSuccess(state: FlagsClientState)

    fun onFailure(error: Throwable, state: FlagsClientState)
}
