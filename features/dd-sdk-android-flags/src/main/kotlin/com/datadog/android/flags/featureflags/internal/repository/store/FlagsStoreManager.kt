/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.store

import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import java.util.concurrent.atomic.AtomicReference

internal class FlagsStoreManager : StoreManager {
    private val featureFlagState = AtomicReference<Map<String, PrecomputedFlag>>(emptyMap())

    override fun getPrecomputedFlag(key: String): PrecomputedFlag? {
        val result = featureFlagState.get()[key]
        return result
    }

    override fun updateFlagsState(flags: Map<String, PrecomputedFlag>) {
        featureFlagState.set(flags)
    }
}
