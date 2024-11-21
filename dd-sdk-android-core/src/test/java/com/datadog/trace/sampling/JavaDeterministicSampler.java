/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.sampling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datadog.android.core.sampling.Sampler;

/**
 * This is a pseudo-duplicate of the java implementation for testing purposes only to ensure
 * compatibility between our generic implementation and the one in our backend agent.
 */
public class JavaDeterministicSampler implements Sampler<Long> {

    private static final long KNUTH_FACTOR = 1111111111111111111L;

    private static final double MAX = Math.pow(2, 64) - 1;

    private final float rate;

    public JavaDeterministicSampler(float rate) {
        this.rate = rate;
    }

    @Override
    public boolean sample(@NonNull Long item) {
        return item * KNUTH_FACTOR + Long.MIN_VALUE < cutoff(rate);
    }

    @Nullable
    @Override
    public Float getSampleRate() {
        return rate;
    }

    private long cutoff(double rate) {
        if (rate < 0.5) {
            return (long) (rate * MAX) + Long.MIN_VALUE;
        }
        if (rate < 1.0) {
            return (long) ((rate * MAX) + Long.MIN_VALUE);
        }
        return Long.MAX_VALUE;
    }
}
