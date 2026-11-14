/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.internal.rum.rumContext

/** Shared per Flags feature; RUM app changes invalidate clients without polling core on evaluation. */
internal class ResponseSigningContext : FeatureContextUpdateReceiver {
    class Snapshot(val applicationId: String?, val active: Boolean)
    val lock = Any()
    private var identity = Snapshot(null, true)

    fun snapshot(): Snapshot = synchronized(lock) { identity }

    fun start() = synchronized(lock) { identity = Snapshot(null, true) }

    fun stop() = synchronized(lock) { identity = Snapshot(null, false) }

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName != Feature.RUM_FEATURE_NAME) return
        val applicationId = context.rumContext()?.applicationId
        synchronized(lock) {
            if (identity.active && applicationId != identity.applicationId) {
                identity = Snapshot(applicationId, true)
            }
        }
    }
}
