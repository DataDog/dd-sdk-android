/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.common.sampling;

import com.datadog.opentracing.DDSpan;
import com.datadog.trace.api.Config;

import java.util.Properties;

/** Main interface to sample a collection of traces. */
public interface Sampler {

  /**
   * Sample a collection of traces based on the parent span
   *
   * @param span the parent span with its context
   * @return true when the trace/spans has to be reported/written
   */
  boolean sample(DDSpan span);

  final class Builder {
    public static Sampler forConfig(final Config config) {
      Sampler sampler;
      if (config != null) {
        if (config.isPrioritySamplingEnabled()) {
          Double samplingRate = config.getTraceSampleRate();
          if (samplingRate != null) {
            sampler = new RateByServiceSampler(config.getTraceSampleRate());
          } else {
            sampler = new RateByServiceSampler();
          }
        } else {
          sampler = new AllSampler();
        }
      } else {
        sampler = new AllSampler();
      }
      return sampler;
    }

    public static Sampler forConfig(final Properties config) {
      return forConfig(Config.get(config));
    }

    private Builder() {}
  }
}
