/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.exposure

import dev.openfeature.kotlin.sdk.ProviderMetadata

/** Identifies evaluations of a [com.datadog.android.flags.openfeature.DatadogFlagsProvider] in hooks. */
internal class DatadogProviderMetadata(
    override val name: String,
    val exposureDecisions: ExposureDecisions
) : ProviderMetadata

internal object ExposureMetadata {
    const val FEATURE_PREFIX = "__dd_feature."
    const val DO_LOG = "__dd_do_log"
    const val EXPOSURE_CACHE_HIT = "__dd_exposure_cache_hit"
    const val ALLOCATION_KEY = "allocationKey"
}
