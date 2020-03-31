/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.sampling

internal interface Sampler {

    /**
     * Sampling method.
     * @return true if you want to keep the value, false otherwise.
     */
    fun sample(): Boolean
}
