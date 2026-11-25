/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

/**
 * Receives each completed flag evaluation from an [ExposureHook].
 */
fun interface ExposureCallback {
    /**
     * Called on the evaluating thread for each evaluation of a [DatadogFlagsProvider] that reaches the hook,
     * including errors and repeats. A `finallyAfter` hook that throws earlier in the final stage skips this call.
     * Do not do I/O here: hand the data to a background queue.
     *
     * @param evaluation the evaluation and its Datadog exposure decision.
     */
    fun onEvaluation(evaluation: ExposureEvaluation)
}
