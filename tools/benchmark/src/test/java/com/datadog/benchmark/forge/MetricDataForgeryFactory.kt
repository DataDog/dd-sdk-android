/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData
import io.opentelemetry.sdk.resources.Resource

class MetricDataForgeryFactory : ForgeryFactory<MetricData> {
    override fun getForgery(forge: Forge): MetricData {
        return ImmutableMetricData.createDoubleGauge(
            Resource.empty(),
            InstrumentationScopeInfo.empty(),
            forge.aString(),
            forge.aString(),
            forge.aString(),
            forge.getForgery()
        )
    }
}
