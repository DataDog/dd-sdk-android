/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;

public class SpanTypeDecorator extends AbstractDecorator {

  public SpanTypeDecorator() {
    super();
    setMatchingTag(DDTags.SPAN_TYPE);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    context.setSpanType(String.valueOf(value));
    return false;
  }
}
