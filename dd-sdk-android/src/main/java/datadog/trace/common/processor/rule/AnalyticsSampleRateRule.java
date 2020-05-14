/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.trace.common.processor.rule;

import datadog.opentracing.DDSpan;
import datadog.trace.api.DDTags;
import datadog.trace.common.processor.TraceProcessor;
import java.util.Collection;
import java.util.Map;

/** Converts analytics sample rate tag to metric */
public class AnalyticsSampleRateRule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"AnalyticsSampleRateDecorator"};
  }

  @Override
  public void processSpan(
      final DDSpan span, final Map<String, Object> tags, final Collection<DDSpan> trace) {
    final Object sampleRateValue = tags.get(DDTags.ANALYTICS_SAMPLE_RATE);
    if (sampleRateValue instanceof Number) {
      span.context().setMetric(DDTags.ANALYTICS_SAMPLE_RATE, (Number) sampleRateValue);
    } else if (sampleRateValue instanceof String) {
      try {
        span.context()
            .setMetric(DDTags.ANALYTICS_SAMPLE_RATE, Double.parseDouble((String) sampleRateValue));
      } catch (final NumberFormatException ex) {
        // ignore
      }
    }

    if (tags.containsKey(DDTags.ANALYTICS_SAMPLE_RATE)) {
      span.setTag(DDTags.ANALYTICS_SAMPLE_RATE, (String) null); // Remove the tag
    }
  }
}
