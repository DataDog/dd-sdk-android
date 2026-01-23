/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import io.opentelemetry.sdk.metrics.data.GaugeData
import io.opentelemetry.sdk.metrics.data.PointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData

class GaugeDataForgeryFactory : ForgeryFactory<GaugeData<out PointData>> {
    override fun getForgery(forge: Forge): GaugeData<out PointData> {
        val data: List<PointData> = forge.aList {
            forge.getForgery()
        }
        return ImmutableGaugeData.create(data)
    }
}
