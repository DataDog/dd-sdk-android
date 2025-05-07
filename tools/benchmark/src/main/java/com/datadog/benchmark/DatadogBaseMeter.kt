/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark

/**
 * Interface to be used for Datadog SDK meters.
 */
interface DatadogBaseMeter {

    /**
     * Starts the Datadog SDK meter.
     */
    fun startMeasuring()

    /**
     * Stops the Datadog SDK meter.
     */
    fun stopMeasuring()
}
