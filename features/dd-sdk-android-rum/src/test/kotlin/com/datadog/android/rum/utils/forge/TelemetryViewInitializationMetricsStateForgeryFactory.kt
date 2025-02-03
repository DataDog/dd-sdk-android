/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.internal.metric.NoValueReason
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsConfig
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsState
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TelemetryViewInitializationMetricsStateForgeryFactory : ForgeryFactory<ViewInitializationMetricsState> {
    override fun getForgery(forge: Forge): ViewInitializationMetricsState {
        val initializationTime = forge.aNullable { forge.aLong(min = 0L) }
        return ViewInitializationMetricsState(
            initializationTime = initializationTime,
            config = forge.aValueFrom(ViewInitializationMetricsConfig::class.java),
            noValueReason = forge.anElementFrom(
                forge.aValueFrom(NoValueReason.TimeToNetworkSettle::class.java),
                forge.aValueFrom(NoValueReason.InteractionToNextView::class.java)
            ).takeIf { initializationTime == null }
        )
    }
}
