package com.datadog.android.core.internal.sampling

internal interface Sampler {

    /**
     * Sampling method.
     * @return true if you want to keep the value, false otherwise.
     */
    fun sample(): Boolean
}
