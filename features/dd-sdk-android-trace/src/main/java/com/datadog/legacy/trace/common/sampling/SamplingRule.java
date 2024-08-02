/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.legacy.trace.common.sampling;

import com.datadog.opentracing.DDSpan;

import java.util.regex.Pattern;

public abstract class SamplingRule {
  private final com.datadog.legacy.trace.common.sampling.RateSampler sampler;

  public SamplingRule(final com.datadog.legacy.trace.common.sampling.RateSampler sampler) {
    this.sampler = sampler;
  }

  public abstract boolean matches(DDSpan span);

  public boolean sample(final DDSpan span) {
    return sampler.sample(span);
  }

  public com.datadog.legacy.trace.common.sampling.RateSampler getSampler() {
    return sampler;
  }

  public static class AlwaysMatchesSamplingRule extends SamplingRule {

    public AlwaysMatchesSamplingRule(final com.datadog.legacy.trace.common.sampling.RateSampler sampler) {
      super(sampler);
    }

    @Override
    public boolean matches(final DDSpan span) {
      return true;
    }
  }

  public abstract static class PatternMatchSamplingRule extends SamplingRule {
    private final Pattern pattern;

    public PatternMatchSamplingRule(final String regex, final com.datadog.legacy.trace.common.sampling.RateSampler sampler) {
      super(sampler);
      this.pattern = Pattern.compile(regex);
    }

    @Override
    public boolean matches(final DDSpan span) {
      final String relevantString = getRelevantString(span);
      return relevantString != null && pattern.matcher(relevantString).matches();
    }

    protected abstract String getRelevantString(DDSpan span);
  }

  public static class ServiceSamplingRule extends PatternMatchSamplingRule {
    public ServiceSamplingRule(final String regex, final com.datadog.legacy.trace.common.sampling.RateSampler sampler) {
      super(regex, sampler);
    }

    @Override
    protected String getRelevantString(final DDSpan span) {
      return span.getServiceName();
    }
  }

  public static class OperationSamplingRule extends PatternMatchSamplingRule {
    public OperationSamplingRule(final String regex, final RateSampler sampler) {
      super(regex, sampler);
    }

    @Override
    protected String getRelevantString(final DDSpan span) {
      return span.getOperationName();
    }
  }
}
