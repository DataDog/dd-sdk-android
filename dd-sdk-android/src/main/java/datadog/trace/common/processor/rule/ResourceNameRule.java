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

/** Converts resource name tag to field */
public class ResourceNameRule implements TraceProcessor.Rule {
  @Override
  public String[] aliases() {
    return new String[] {"ResourceNameDecorator"};
  }

  @Override
  public void processSpan(
      final DDSpan span, final Map<String, Object> tags, final Collection<DDSpan> trace) {
    final Object name = tags.get(DDTags.RESOURCE_NAME);
    if (name != null) {
      span.setResourceName(name.toString());
    }

    if (tags.containsKey(DDTags.RESOURCE_NAME)) {
      span.setTag(DDTags.RESOURCE_NAME, (String) null); // Remove the tag
    }
  }
}
