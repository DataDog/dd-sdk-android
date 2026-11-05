/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

import com.datadog.android.lint.InternalApi

/**
 * An assignment carrying the configuration's evaluation telemetry consent.
 * Bridge consumers must preserve this value when replaying a snapshot evaluation.
 * Older [UnparsedFlag] implementations are treated as protected.
 */
@InternalApi
interface UnparsedFlagWithConsent : UnparsedFlag {
    /** Whether the evaluated configuration explicitly opted into full evaluation data. */
    val observeFullEvaluationData: Boolean
}
