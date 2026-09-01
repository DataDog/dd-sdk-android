/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.provider

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.vitals.VitalReader

internal class VitalReaderWrapper(
    private val vitalReader: VitalReader,
    timeProvider: TimeProvider,
    override val intervalMs: Long
) : DataPointsReader<Double>(timeProvider) {

    override fun readValue(): Double? = vitalReader.readVitalData()
}
