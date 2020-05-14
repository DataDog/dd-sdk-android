/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.trace.common.processor.rule;

import datadog.opentracing.DDSpan;
import datadog.trace.common.processor.TraceProcessor;
import io.opentracing.tag.Tags;
import java.util.Collection;
import java.util.Map;

/** This span decorator protect against spam on the resource name */
public class Status404Rule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"Status404Decorator"};
  }

  @Override
  public void processSpan(
      final DDSpan span, final Map<String, Object> tags, final Collection<DDSpan> trace) {
    final Object httpStatus = tags.get(Tags.HTTP_STATUS.getKey());
    if (!span.context().isResourceNameSet()
        && httpStatus != null
        && (httpStatus.equals(404) || httpStatus.equals("404"))) {
      span.setResourceName("404");
    }
  }
}
