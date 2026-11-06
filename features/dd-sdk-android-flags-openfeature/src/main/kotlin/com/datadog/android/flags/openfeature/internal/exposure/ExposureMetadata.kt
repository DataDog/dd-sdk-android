/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.exposure

import com.datadog.android.api.InternalLogger
import dev.openfeature.kotlin.sdk.ProviderMetadata

/** Identifies evaluations of a [com.datadog.android.flags.openfeature.DatadogFlagsProvider] in hooks. */
internal class DatadogProviderMetadata(
    override val name: String,
    val exposureDecisions: ExposureDecisions,
    val internalLogger: InternalLogger
) : ProviderMetadata

internal object ExposureMetadata {
    const val FEATURE_PREFIX = "__dd_feature."
    const val ALLOCATION_KEY = "allocationKey"
}
