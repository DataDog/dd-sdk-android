/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.sampling

import androidx.annotation.FloatRange

/**
 * Interface representing the sampling.
 * @param T the type of items to sample.
 */
interface Sampler<T : Any> {

    /**
     * @param item the item to sample
     * @return true to keep the item, false to discard it
     */
    fun sample(item: T): Boolean

    /**
     * @return the sample rate if applicable, as a float between 0 and 100,
     * or null if not applicable
     */
    @FloatRange(from = 0.0, to = 100.0)
    fun getSampleRate(): Float?
}
