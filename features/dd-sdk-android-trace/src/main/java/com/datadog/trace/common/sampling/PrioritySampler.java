package com.datadog.trace.common.sampling;

import com.datadog.trace.core.CoreSpan;

public interface PrioritySampler {
  <T extends CoreSpan<T>> void setSamplingPriority(T span);
}
