/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumViewType
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class VitalReaderRunnable(
    val sdkCore: FeatureSdkCore,
    private val reader: VitalReader,
    val observer: VitalObserver,
    val executor: ScheduledExecutorService,
    private val periodMs: Long
) : Runnable, FeatureContextUpdateReceiver {

    @Volatile
    internal var currentRumContext: RumContext? = null

    override fun run() {
        val rumViewType = currentRumContext?.viewType
        if (rumViewType == RumViewType.FOREGROUND) {
            val data = reader.readVitalData()
            if (data != null) {
                observer.onNewSample(data)
            }
        }
        executor.scheduleSafe(
            "Vitals monitoring",
            periodMs,
            TimeUnit.MILLISECONDS,
            sdkCore.internalLogger,
            this
        )
    }

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            currentRumContext = RumContext.fromFeatureContext(context)
        }
    }
}
