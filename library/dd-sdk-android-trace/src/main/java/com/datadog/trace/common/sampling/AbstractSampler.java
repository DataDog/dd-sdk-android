/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.common.sampling;

import com.datadog.opentracing.DDSpan;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

@Deprecated
public abstract class AbstractSampler implements Sampler {

  /** Sample tags */
  protected Map<String, Pattern> skipTagsPatterns = new HashMap<>();

  @Override
  public boolean sample(final DDSpan span) {

    // Filter by tag values
    for (final Entry<String, Pattern> entry : skipTagsPatterns.entrySet()) {
      final Object value = span.getTags().get(entry.getKey());
      if (value != null) {
        final String strValue = String.valueOf(value);
        final Pattern skipPattern = entry.getValue();
        if (skipPattern.matcher(strValue).matches()) {
          return false;
        }
      }
    }

    return doSample(span);
  }

  /**
   * Pattern based skipping of tag values
   *
   * @param tag
   * @param skipPattern
   */
  @Deprecated
  public void addSkipTagPattern(final String tag, final Pattern skipPattern) {
    skipTagsPatterns.put(tag, skipPattern);
  }

  protected abstract boolean doSample(DDSpan span);
}
