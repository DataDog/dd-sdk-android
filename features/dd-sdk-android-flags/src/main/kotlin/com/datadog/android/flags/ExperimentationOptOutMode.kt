/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/** Controls whether the client honors the subject's `dd_experimentation_opt_out_at` attribute. */
enum class ExperimentationOptOutMode {
    /** Ignores the opt-out attribute. This is the default. */
    DISABLED,

    /**
     * A valid RFC3339 timestamp excludes the subject immediately from experiment allocations.
     * A future timestamp also takes effect immediately. Omit the attribute to permit experimentation.
     * An invalid timestamp returns application defaults with an INVALID_CONTEXT error.
     * This does not suppress evaluation telemetry.
     */
    IMMEDIATE
}
