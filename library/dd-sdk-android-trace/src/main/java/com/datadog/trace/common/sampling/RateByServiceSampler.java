/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.common.sampling;

import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;

import com.datadog.opentracing.DDSpan;
import com.datadog.trace.api.sampling.PrioritySampling;
import java.util.Map;

/**
 * A rate sampler which maintains different sample rates per service+env name.
 *
 * <p>The configuration of (serviceName,env)->rate is configured by the core agent.
 */
public class RateByServiceSampler implements Sampler, PrioritySampler {
  public static final String SAMPLING_AGENT_RATE = "_dd.agent_psr";

  /** Key for setting the default/baseline rate */
  private static final String DEFAULT_KEY = "service:,env:";

  private static final double DEFAULT_RATE = 1.0;

  private volatile Map<String, RateSampler> serviceRates;

  public RateByServiceSampler() {
    this(DEFAULT_RATE);
  }

  public RateByServiceSampler(Double defaultSampleRate) {
      this.serviceRates = singletonMap(DEFAULT_KEY, createRateSampler(defaultSampleRate));
  }

  @Override
  public boolean sample(final DDSpan span) {
    // Priority sampling sends all traces to the core agent, including traces marked dropped.
    // This allows the core agent to collect stats on all traces.
    return true;
  }

  /** If span is a root span, set the span context samplingPriority to keep or drop */
  @Override
  public void setSamplingPriority(final DDSpan span) {
    final String serviceName = span.getServiceName();
    final String env = getSpanEnv(span);
    final String key = "service:" + serviceName + ",env:" + env;

    final Map<String, RateSampler> rates = serviceRates;
    RateSampler sampler = serviceRates.get(key);
    if (sampler == null) {
      sampler = rates.get(DEFAULT_KEY);
    }

    final boolean priorityWasSet;

    if (sampler.sample(span)) {
      priorityWasSet = span.context().setSamplingPriority(PrioritySampling.SAMPLER_KEEP);
    } else {
      priorityWasSet = span.context().setSamplingPriority(PrioritySampling.SAMPLER_DROP);
    }

    // Only set metrics if we actually set the sampling priority
    // We don't know until the call is completed because the lock is internal to DDSpanContext
    if (priorityWasSet) {
      span.context().setMetric(SAMPLING_AGENT_RATE, sampler.getSampleRate());
    }
  }

  private static String getSpanEnv(final DDSpan span) {
    return null == span.getTags().get("env") ? "" : String.valueOf(span.getTags().get("env"));
  }

  private RateSampler createRateSampler(final double sampleRate) {
    final double sanitizedRate;
    if (sampleRate < 0) {
      sanitizedRate = 1;
    } else if (sampleRate > 1) {
      sanitizedRate = 1;
    } else {
      sanitizedRate = sampleRate;
    }

    return new DeterministicSampler(sanitizedRate);
  }
}
