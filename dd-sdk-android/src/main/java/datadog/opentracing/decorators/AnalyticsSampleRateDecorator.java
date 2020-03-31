/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;

public class AnalyticsSampleRateDecorator extends AbstractDecorator {
  public AnalyticsSampleRateDecorator() {
    super();
    setMatchingTag(DDTags.ANALYTICS_SAMPLE_RATE);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    if (value instanceof Number) {
      context.setMetric(DDTags.ANALYTICS_SAMPLE_RATE, (Number) value);
    } else if (value instanceof String) {
      try {
        context.setMetric(DDTags.ANALYTICS_SAMPLE_RATE, Double.parseDouble((String) value));
      } catch (final NumberFormatException ex) {
        // ignore
      }
    }
    return false;
  }
}
