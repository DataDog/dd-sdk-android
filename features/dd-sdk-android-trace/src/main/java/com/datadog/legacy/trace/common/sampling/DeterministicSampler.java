/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.legacy.trace.common.sampling;

import com.datadog.opentracing.DDSpan;
import com.datadog.opentracing.DDTracer;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * This implements the deterministic sampling algorithm used by the Datadog Agent as well as the
 * tracers for other languages
 */
public class DeterministicSampler implements RateSampler {
    private static final BigInteger KNUTH_FACTOR = new BigInteger("1111111111111111111");
    private static final BigDecimal SPAN_ID_MAX_AS_BIG_DECIMAL =
            new BigDecimal(DDTracer.TRACE_ID_64_BITS_MAX);
    private static final BigInteger MODULUS = new BigInteger("2").pow(64);

    private final BigInteger cutoff;
    private final double rate;

    public DeterministicSampler(final double rate) {
        this.rate = rate;
        cutoff = new BigDecimal(rate).multiply(SPAN_ID_MAX_AS_BIG_DECIMAL).toBigInteger();
    }

    @Override
    public boolean sample(final DDSpan span) {
        final boolean sampled;
        if (rate == 1) {
            sampled = true;
        } else if (rate == 0) {
            sampled = false;
        } else {
            // we are going to use the spanId here instead as it is 64 bits and will not affect the
            // sampling decision. This is the same approach used by the new CoreTracer code.
            sampled = span.getSpanId().multiply(KNUTH_FACTOR).mod(MODULUS).compareTo(cutoff) < 0;
        }

        return sampled;
    }

    @Override
    public double getSampleRate() {
        return rate;
    }
}
