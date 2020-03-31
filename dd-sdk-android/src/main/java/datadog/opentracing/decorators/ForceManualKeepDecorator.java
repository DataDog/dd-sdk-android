/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;
import datadog.trace.api.sampling.PrioritySampling;

/**
 * Tag decorator to replace tag 'manual.keep: true' with the appropriate priority sampling value.
 */
public class ForceManualKeepDecorator extends AbstractDecorator {

  public ForceManualKeepDecorator() {
    super();
    setMatchingTag(DDTags.MANUAL_KEEP);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    if (value instanceof Boolean && (boolean) value) {
      context.setSamplingPriority(PrioritySampling.USER_KEEP);
    } else if (value instanceof String && Boolean.parseBoolean((String) value)) {
      context.setSamplingPriority(PrioritySampling.USER_KEEP);
    }
    return false;
  }
}
